# ===========================================================================
# FairValue Engine — PricingResult Pydantic 모델 (W6, 계약 ③)
# ---------------------------------------------------------------------------
# 패치 반영:
#   - B-3: 표준 component key 12종만(약식 명칭 금지).
#   - 2.4: warnings/errors 는 default_factory=list.
#   - F-1: @model_validator 로 Σ(components 제외 total) == total_fair_value 강제(허용오차 0.01).
#   - F-8: 부호 규칙 강제 — issuer_call_value / sale_claim_value / dilution_effect ≤ 0.
#   - 2.1: parity 는 key_parameters.parity 에만(입력 아님).
# ===========================================================================
from __future__ import annotations

from datetime import date, datetime
from typing import Literal, Optional

from pydantic import BaseModel, ConfigDict, Field, model_validator

# Σ=total 허용오차(절대값)
SUM_TOLERANCE = 0.01

# total 을 제외한 합산 대상 component key
_SUM_KEYS = (
    "bond_value",
    "preferred_share_value",
    "conversion_option_value",
    "exchange_option_value",
    "warrant_value",
    "redemption_option_value",
    "issuer_call_value",
    "sale_claim_value",
    "stock_option_value",
    "conditional_option_value",
    "dilution_effect",
)

# 부호가 음수(≤0)여야 하는 component key(§4.10)
_NEGATIVE_KEYS = ("issuer_call_value", "sale_claim_value", "dilution_effect")


class Components(BaseModel):
    model_config = ConfigDict(extra="forbid")
    bond_value: Optional[float] = None
    preferred_share_value: Optional[float] = None
    conversion_option_value: Optional[float] = None
    exchange_option_value: Optional[float] = None
    warrant_value: Optional[float] = None
    redemption_option_value: Optional[float] = None
    issuer_call_value: Optional[float] = None      # 음수
    sale_claim_value: Optional[float] = None       # 음수
    stock_option_value: Optional[float] = None
    conditional_option_value: Optional[float] = None
    dilution_effect: Optional[float] = None        # 음수
    total_fair_value: Optional[float] = None        # = 나머지 부호포함 합의 echo

    @model_validator(mode="after")
    def _check_sign_rules(self) -> "Components":
        """F-8: 음수 강제 key 가 양수면 거부."""
        for key in _NEGATIVE_KEYS:
            val = getattr(self, key)
            if val is not None and val > 0:
                raise ValueError(
                    f"부호 규칙 위반: {key}={val} 는 0 이하여야 합니다(§4.10)."
                )
        return self

    @model_validator(mode="after")
    def _check_sum_equals_total(self) -> "Components":
        """F-1: Σ(components 제외 total) == total_fair_value (허용오차 0.01)."""
        if self.total_fair_value is None:
            return self
        s = sum(
            (getattr(self, k) or 0.0) for k in _SUM_KEYS
        )
        if abs(s - self.total_fair_value) > SUM_TOLERANCE:
            raise ValueError(
                f"합계 불변식 위반: Σcomponents={s:.4f} != total_fair_value="
                f"{self.total_fair_value:.4f} (허용오차 {SUM_TOLERANCE})."
            )
        return self


class KeyParameters(BaseModel):
    model_config = ConfigDict(extra="forbid")
    risk_free_rate: Optional[float] = None
    ytm: Optional[float] = None
    credit_spread: Optional[float] = None
    volatility: Optional[float] = None
    dividend_yield: Optional[float] = None
    parity: Optional[float] = None              # 엔진 산출(입력 아님)
    discount_rate: Optional[float] = None
    model_name: str
    model_version: str
    simulation_paths: Optional[int] = None
    lattice_steps: Optional[int] = None


class AuditData(BaseModel):
    model_config = ConfigDict(extra="forbid")
    cashflow_schedule: list[dict] = Field(default_factory=list)
    discount_factors: list[dict] = Field(default_factory=list)
    curve_snapshot: dict = Field(default_factory=dict)
    node_summary: dict = Field(default_factory=dict)
    path_summary: dict = Field(default_factory=dict)
    exercise_log: list[dict] = Field(default_factory=list)
    calculation_trace: list[dict] = Field(default_factory=list)
    input_snapshot_hash: Optional[str] = None


class Reproducibility(BaseModel):
    model_config = ConfigDict(extra="forbid")
    input_hash: str
    seed: int
    model_version: str
    engine_commit: Optional[str] = None
    computed_at: Optional[datetime] = None


class Issue(BaseModel):
    model_config = ConfigDict(extra="forbid")
    code: str
    message: str
    field: Optional[str] = None
    stage: Optional[str] = None


class PricingResult(BaseModel):
    model_config = ConfigDict(extra="forbid")
    job_id: int
    instrument_id: int
    instrument_type: Literal["RCPS", "CPS", "CB", "EB", "BW", "SO", "CSO"]
    valuation_date: date
    status: Literal["DONE", "FAILED", "PARTIAL"]
    total_fair_value: Optional[float] = None
    per_unit_value: Optional[float] = None
    components: Components
    key_parameters: KeyParameters
    sensitivity_summary: Optional[dict] = None
    scenario_summary: Optional[dict] = None
    audit_data: Optional[AuditData] = None
    reproducibility: Reproducibility
    warnings: list[Issue] = Field(default_factory=list)
    errors: list[Issue] = Field(default_factory=list)

    @model_validator(mode="after")
    def _check_top_total_matches_components(self) -> "PricingResult":
        """top-level total_fair_value 와 components.total_fair_value 정합(있을 때)."""
        if (
            self.total_fair_value is not None
            and self.components.total_fair_value is not None
            and abs(self.total_fair_value - self.components.total_fair_value) > SUM_TOLERANCE
        ):
            raise ValueError(
                f"total_fair_value({self.total_fair_value}) 와 "
                f"components.total_fair_value({self.components.total_fair_value}) 불일치."
            )
        return self
