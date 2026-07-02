# ===========================================================================
# FairValue Engine — Goldman-Sachs 격자 (엔진확장-2, 전환확률 가중 할인)
# ---------------------------------------------------------------------------
# GS(Goldman-Sachs, 1994) 전환사채/복합상품 모형. TF(요소분리 이중할인)와 달리
# 지분/부채를 분리하지 않고, ★매 노드의 전환확률 q_{i,j} 로 위험조정할인율을 만들어
# 단일 트리를 backward induction 한다.
#
#   위험조정할인율:  y_{i,j} = q_{i,j}·rf + (1 − q_{i,j})·rd   (연속복리, 노드별)
#     - 전환이 확실(q=1)한 노드 → rf(무위험) 로 할인(지분처럼).
#     - 미전환 확실(q=0)한 노드 → rd(위험) 로 할인(부채처럼).
#     - 중간은 전환확률로 가중(probability of conversion-weighted discounting).
#
#   전환확률 q_{i,j} 전파(GS 핵심):
#     - 만기: V_T = max(전환가치, 상환가치). 전환우위면 q=1, 아니면 q=0.
#     - backward: q_cont = p·q_{자식up} + (1−p)·q_{자식down}  (위험중립 기대).
#         이 q_cont 로 y 산정 → 연속가치 할인.
#         노드에서 조기 전환 최적이면 q=1 리셋, 풋(현금) 최적이면 q=0 리셋,
#         발행자 콜 대응도 보유자 전환이면 q=1·현금이면 q=0(전환/풋과 동일 원칙).
#
# TF 와 공유: CRR 이항트리(u=e^{σ√Δt}, d=1/u, p=(e^{(rf−q)Δt}−d)/(u−d)),
#   전환/상환/콜 최적행사 조건. 차이는 "할인율을 어떻게 정하느냐"뿐.
#
# ★ 공개 문헌 기반 독립 구현(비공개 산식 단정 아님). CBLatticeSpec 재사용(같은 입력).
#   rf_steps/rd_steps 주입 인터페이스 유지(커브 term-structure 결선 대비).
# pure Python(math 만). 새 의존성 없음.
# ===========================================================================
from __future__ import annotations

import math

from .tf_lattice import CBLatticeSpec, _coupon_steps


def _gs_backward(spec: CBLatticeSpec, rf_steps=None, rd_steps=None, keep_trees: bool = False):
    """GS 단일 트리 backward induction.

    반환:
      keep_trees=False → (V0, Q0)  (루트 가치, 루트 전환확률)
      keep_trees=True  → (V0, Q0, trees_raw) — 스텝별 S/V/Q 원시배열(패딩 전)
    """
    n = spec.steps
    dt = spec.t_years / n
    u = math.exp(spec.sigma * math.sqrt(dt))
    d = 1.0 / u
    u2 = u * u
    cpn = (spec.coupon_per_year / spec.freq) if spec.freq > 0 else 0.0
    coupon_at = _coupon_steps(spec, dt)

    def rf_at(step):
        return rf_steps[step] if rf_steps is not None else spec.rf

    def rd_at(step):
        return rd_steps[step] if rd_steps is not None else spec.rd

    if keep_trees:
        und_by_step = [None] * (n + 1)
        val_by_step = [None] * (n + 1)
        q_by_step = [None] * (n + 1)
        probs = [None] * n

    # 만기(step=n) payoff: V = max(전환, 상환). 전환우위 → q=1.
    V = [0.0] * (n + 1)
    Q = [0.0] * (n + 1)
    und_n = [0.0] * (n + 1)
    s = spec.s0 * (d ** n)
    for j in range(n + 1):
        und_n[j] = s
        redemption = spec.face + cpn
        conv = spec.conv_ratio * s if spec.conv_enabled else -1.0
        if conv >= redemption:
            V[j] = conv
            Q[j] = 1.0
        else:
            V[j] = redemption
            Q[j] = 0.0
        s *= u2
    if keep_trees:
        und_by_step[n] = und_n
        val_by_step[n] = V[:]
        q_by_step[n] = Q[:]

    for step in range(n - 1, -1, -1):
        rf = rf_at(step)
        rd = rd_at(step)
        p = (math.exp((rf - spec.q) * dt) - d) / (u - d)
        if keep_trees:
            probs[step] = p
        nV = [0.0] * (step + 1)
        nQ = [0.0] * (step + 1)
        und_step = [0.0] * (step + 1)
        t = step * dt
        s = spec.s0 * (d ** step)
        for j in range(step + 1):
            und_step[j] = s
            # 전환확률 위험중립 전파(자식 q 의 기대) → 위험조정할인율 y.
            q_cont = p * Q[j + 1] + (1 - p) * Q[j]
            y = q_cont * rf + (1 - q_cont) * rd
            cont = math.exp(-y * dt) * (p * V[j + 1] + (1 - p) * V[j])
            if step in coupon_at:
                cont += cpn                       # 쿠폰(현금) 수령
            val, qh = cont, q_cont

            # 전환(보유자, 지분): 최적이면 q=1 리셋.
            if spec.conv_enabled and t >= spec.conv_start_t - 1e-12:
                conv = spec.conv_ratio * s
                if conv >= val:
                    val, qh = conv, 1.0

            # 풋(보유자, 현금): 최적이면 q=0 리셋.
            if spec.put_enabled and t >= spec.put_start_t - 1e-12:
                if spec.put_price > val:
                    val, qh = spec.put_price, 0.0

            # 발행자 콜(발행자 가치 최소화): 보유자는 max(콜가, 전환) 강제.
            #   전환 택하면 지분(q=1), 현금(콜가) 수령이면 부채(q=0) 로 리셋.
            if spec.call_enabled and t >= spec.call_start_t - 1e-12:
                if val > spec.call_price:
                    conv = spec.conv_ratio * s if spec.conv_enabled else 0.0
                    if conv >= spec.call_price:
                        val, qh = conv, 1.0
                    else:
                        val, qh = spec.call_price, 0.0

            nV[j] = val
            nQ[j] = qh
            s *= u2
        if keep_trees:
            und_by_step[step] = und_step
            val_by_step[step] = nV[:]
            q_by_step[step] = nQ[:]
        V, Q = nV, nQ

    if keep_trees:
        return V[0], Q[0], {
            "u": u, "d": d, "dt": dt,
            "und": und_by_step, "val": val_by_step, "q": q_by_step, "probs": probs,
        }
    return V[0], Q[0]


def gs_value(spec: CBLatticeSpec, rf_steps=None, rd_steps=None) -> float:
    """GS 격자 루트 가치. 옵션 플래그 토글로 telescoping component 분해에 재사용."""
    v0, _q0 = _gs_backward(spec, rf_steps, rd_steps, keep_trees=False)
    return v0


def gs_value_split(spec: CBLatticeSpec, rf_steps=None, rd_steps=None) -> tuple[float, float]:
    """GS 는 요소분리를 하지 않지만, 인터페이스 정합(TF 의 (Vb,Ve))을 위해
    루트 전환확률로 (부채분, 지분분) = ((1−q0)·V0, q0·V0) 분해해 반환.
    합 = V0(총가치). 진짜 부채/지분 분리가 아니라 '전환확률 가중 표현'."""
    v0, q0 = _gs_backward(spec, rf_steps, rd_steps, keep_trees=False)
    return (1.0 - q0) * v0, q0 * v0


def gs_value_with_trees(spec: CBLatticeSpec, rf_steps=None, rd_steps=None):
    """GS 격자 + 스텝별 트리. 반환 (debt0, equity0, trees).
    trees = underlying/equity/debt/composite/conversion_prob_tree/risk_neutral_prob/tree_meta.
      - composite_tree = V (상품 가치)
      - conversion_prob_tree = q_{i,j} (★GS 고유 — 요소분리 대체)
      - equity_tree = q·V, debt_tree = (1−q)·V (전환확률 가중 분해, TF 6키 호환)
    각 트리 [step][j] 정방향(0→n), 상삼각 외(j>step) None 패딩."""
    n = spec.steps
    v0, q0, raw = _gs_backward(spec, rf_steps, rd_steps, keep_trees=True)
    u, d, dt = raw["u"], raw["d"], raw["dt"]
    und, val, qtree, probs = raw["und"], raw["val"], raw["q"], raw["probs"]

    def pad(rows):
        out = []
        for st in range(n + 1):
            row = rows[st] or []
            out.append([round(v, 4) for v in row] + [None] * (n - st))
        return out

    underlying = pad(und)
    composite = pad(val)
    conv_prob = [
        ([round(qtree[st][j], 6) for j in range(st + 1)] + [None] * (n - st))
        for st in range(n + 1)
    ]
    equity = [
        [(round(qtree[st][j] * val[st][j], 4) if j <= st else None) for j in range(n + 1)]
        for st in range(n + 1)
    ]
    debt = [
        [(round((1.0 - qtree[st][j]) * val[st][j], 4) if j <= st else None) for j in range(n + 1)]
        for st in range(n + 1)
    ]
    prob = [{"step": st, "p": round(probs[st], 6), "q": round(1 - probs[st], 6)} for st in range(n)]
    trees = {
        "underlying_tree": underlying,
        "equity_tree": equity,
        "debt_tree": debt,
        "composite_tree": composite,
        "conversion_prob_tree": conv_prob,
        "risk_neutral_prob": prob,
        "tree_meta": {
            "steps_used": n,
            "dt": round(dt, 6),
            "u": round(u, 6),
            "d": round(d, 6),
            "display_nodes": 11,
            "model": "GS",
        },
    }
    return (1.0 - q0) * v0, q0 * v0, trees
