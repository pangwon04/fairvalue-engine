"""엔진확장-1: 보고서용 트리·위험중립확률 검증(BLOCKING 회귀·구조·정합)."""
import json
import math
from pathlib import Path

import pytest

from app.models.tf_lattice import CBLatticeSpec, tf_value, tf_value_with_trees, report_steps
from app.models.cb_calculator import calculate_cb
from app.models.registry import LIBRARY
from app.result import PricingResult

FIX = Path(__file__).resolve().parent / "fixtures"

TREE_KEYS = ("underlying_tree", "equity_tree", "debt_tree", "composite_tree", "risk_neutral_prob", "tree_meta")


def _spec(steps):
    return CBLatticeSpec(
        s0=3260, sigma=0.45, t_years=2.3, steps=steps, rf=0.033, rd=0.15, q=0.0,
        face=10000, coupon_per_year=200, freq=4, conv_enabled=True, conv_ratio=3.067,
        conv_start_t=0.0, put_enabled=True, put_price=11000, put_start_t=1.0)


def test_regression_root_equals_tf_value():
    """★회귀: tf_value_with_trees 루트(Vb0+Ve0) == tf_value(동일 spec) (1e-6)."""
    for st in (8, 30, 100):
        sp = _spec(st)
        v = tf_value(sp)
        vb0, ve0, _ = tf_value_with_trees(sp)
        assert abs((vb0 + ve0) - v) < 1e-6, f"steps={st}: {vb0+ve0} vs {v}"


def test_tree_structure_upper_triangular():
    """트리 구조: step 수=n+1, 각 step 유효노드=step+1, 상삼각 외 None, underlying[0][0]=s0."""
    n = 12
    _, _, trees = tf_value_with_trees(_spec(n))
    und = trees["underlying_tree"]
    assert len(und) == n + 1
    for st, row in enumerate(und):
        assert len(row) == n + 1                              # 직사각(패딩)
        valid = [x for x in row if x is not None]
        assert len(valid) == st + 1                           # 유효노드 = step+1
        for j in range(st + 1, n + 1):
            assert row[j] is None                             # 상삼각 외 None
    assert abs(und[0][0] - 3260) < 1e-6                       # 루트 = s0


def test_prob_range_and_meta():
    """위험중립확률 0<p<1(무차익) + meta 필드."""
    _, _, trees = tf_value_with_trees(_spec(20))
    prob = trees["risk_neutral_prob"]
    assert len(prob) == 20
    assert all(0.0 < x["p"] < 1.0 for x in prob)
    assert all(abs(x["p"] + x["q"] - 1.0) < 1e-9 for x in prob)
    m = trees["tree_meta"]
    assert m["steps_used"] == 20 and m["u"] > 1 and 0 < m["d"] < 1 and m["display_nodes"] == 11


def test_report_steps_guard():
    """보고서용 steps 상·하한 가드, 사용자 steps 초과 안 함."""
    assert report_steps(2.3, 1000) == 28          # ~30일 간격
    assert report_steps(0.3, 1000) == 20          # 하한 20
    assert report_steps(100.0, 1000) == 250       # 상한 250
    assert report_steps(2.3, 10) == 10            # 사용자 steps 가 더 작으면 그것


def test_composite_root_matches_lattice():
    """composite_tree 루트 = 최종 실행 루트(tf_value, 동일 report steps) (1e-4)."""
    rs = report_steps(2.3, 1000)
    sp = _spec(rs)
    _, _, trees = tf_value_with_trees(sp)
    root = trees["composite_tree"][0][0]
    assert abs(root - tf_value(sp)) < 1e-4


@pytest.mark.parametrize("fixture,fn", [
    ("cb_case1_context.json", "cb"),
    ("rcps_real_2022_context.json", "rcps"),
    ("cps_case1_context.json", "cps"),
    ("eb_case1_context.json", "eb"),
    ("bw_case1_context.json", "bw"),
])
def test_calculator_returns_trees(fixture, fn):
    """각 calculator 결과에 trees(6키) + key_parameters u/d. 12키·모델검증 불변(하위호환)."""
    ctx = json.loads((FIX / fixture).read_text(encoding="utf-8"))
    res = LIBRARY.calculate(ctx)
    trees = res["trees"]
    for k in TREE_KEYS:
        assert k in trees, f"{fn}: {k} 누락"
    m = trees["tree_meta"]
    assert 20 <= m["steps_used"] <= 250
    # EB 는 교환대상(타사주) target_market.spot 이 기초자산.
    exp_spot = float((ctx.get("rights", {}).get("exchange", {}).get("target_market") or ctx["market"])["spot"])
    assert abs(trees["underlying_tree"][0][0] - exp_spot) < 1.0
    assert all(0 < x["p"] < 1 for x in trees["risk_neutral_prob"])
    assert res["key_parameters"]["u"] == m["u"] and res["key_parameters"]["d"] == m["d"]
    # ★ 하위호환: trees·u·d 추가에도 PricingResult 스키마 검증 통과
    pr = PricingResult.model_validate(res)
    assert pr.trees is not None
    assert pr.status == "DONE"


def test_cb_12keys_unchanged_with_trees():
    """★회귀: trees 추가가 CB 12키 값·Σ=total 안 바꿈."""
    ctx = json.loads((FIX / "cb_case1_context.json").read_text(encoding="utf-8"))
    res = calculate_cb(ctx)
    c = res["components"]
    s = sum((c.get(k) or 0.0) for k in (
        "bond_value", "preferred_share_value", "conversion_option_value", "exchange_option_value",
        "warrant_value", "redemption_option_value", "issuer_call_value", "sale_claim_value",
        "stock_option_value", "conditional_option_value", "dilution_effect"))
    assert abs(s - c["total_fair_value"]) <= 0.01
    assert res["reproducibility"]["input_hash"] == ctx.get("input_hash", "0" * 64)
