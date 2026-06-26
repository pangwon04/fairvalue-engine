# ===========================================================================
# FairValue Engine — ValuationContext Pydantic 모델 (W6, 계약 ②)
# ---------------------------------------------------------------------------
# 패치 반영:
#   - extra="forbid": 미정의 필드 차단.
#   - ModelName 에서 TRINOMIAL 제외(패치 2.3).
#   - Conversion 에 parity 없음(패치 2.1 — 결과 전용).
#   - curves 는 포인트 배열 스냅샷(risk_free_curve/credit_curve). *_ref 는 rawForm 전용.
#   - input_hash·model_version 필수.
# ===========================================================================
from __future__ import annotations

from datetime import date, datetime
from typing import Literal, Optional

from pydantic import BaseModel, ConfigDict, Field

InstrumentType = Literal["RCPS", "CPS", "CB", "EB", "BW", "SO", "CSO"]
# TRINOMIAL 제외(미사용)
ModelName = Literal["TF_LATTICE", "LATTICE", "MONTE_CARLO", "LSMC", "BSM", "DCF"]
ExerciseStyle = Literal["AMERICAN", "EUROPEAN", "BERMUDAN"]
CurvePoints = list[tuple[float, float]]  # [(tenor_year, rate_pct), ...]


class Terms(BaseModel):
    model_config = ConfigDict(extra="forbid")
    issue_date: Optional[date] = None
    maturity_date: Optional[date] = None
    issue_amount: Optional[float] = None
    face_value: Optional[float] = None
    issue_price: Optional[float] = None
    coupon_rate: Optional[float] = None
    coupon_freq_month: Optional[Literal[1, 3, 6, 12]] = None
    guaranteed_yield: Optional[float] = None
    redemption_type: Optional[Literal["bullet", "amortizing"]] = None
    interest_payment_type: Optional[Literal["cash", "accrued", "compound"]] = None
    dividend_cumulative: Optional[bool] = None
    host_type: Optional[Literal["dated", "perpetual"]] = None
    grant_date: Optional[date] = None
    grant_quantity: Optional[float] = None
    exercise_price: Optional[float] = None
    expected_term: Optional[float] = None
    tranche: list[dict] = Field(default_factory=list)


class Conversion(BaseModel):
    # parity 필드 없음(패치 2.1). parity 는 PricingResult.key_parameters 에만 존재.
    model_config = ConfigDict(extra="forbid")
    strike: float
    ratio: Optional[float] = None
    start: Optional[date] = None
    end: Optional[date] = None
    style: Optional[ExerciseStyle] = None


class Exchange(BaseModel):
    model_config = ConfigDict(extra="forbid")
    enabled: Optional[bool] = None
    target_asset_id: Optional[int] = None
    ratio: Optional[float] = None
    strike: Optional[float] = None


class Warrant(BaseModel):
    model_config = ConfigDict(extra="forbid")
    enabled: Optional[bool] = None
    strike: Optional[float] = None
    quantity: Optional[float] = None
    separable: Optional[bool] = None
    start: Optional[date] = None
    end: Optional[date] = None


class RedemptionLeg(BaseModel):
    model_config = ConfigDict(extra="forbid", populate_by_name=True)
    enabled: bool = False
    yield_: Optional[float] = Field(default=None, alias="yield")
    start: Optional[date] = None
    style: Optional[ExerciseStyle] = None


class Redemption(BaseModel):
    model_config = ConfigDict(extra="forbid")
    put: Optional[RedemptionLeg] = None
    call: Optional[RedemptionLeg] = None


class IssuerCall(BaseModel):
    model_config = ConfigDict(extra="forbid")
    enabled: bool = False
    strike: Optional[float] = None
    style: Optional[ExerciseStyle] = None
    start: Optional[date] = None


class SaleClaim(BaseModel):
    model_config = ConfigDict(extra="forbid")
    enabled: bool = False
    discount_type: Optional[Literal["standalone_riskfree", "credit"]] = None
    strike_pct: Optional[float] = None
    style: Optional[ExerciseStyle] = None
    beneficiary: Optional[Literal["issuer", "third_party"]] = None


class Refixing(BaseModel):
    model_config = ConfigDict(extra="forbid")
    enabled: bool = False
    start: Optional[date] = None
    step_month: Optional[int] = None
    floor: Optional[float] = None
    init_strike: Optional[float] = None
    direction: Optional[Literal["DOWN", "BOTH"]] = None


class Dilution(BaseModel):
    model_config = ConfigDict(extra="forbid")
    enabled: bool = False
    new_shares: Optional[float] = None


class VestingPoint(BaseModel):
    model_config = ConfigDict(extra="forbid")
    date: date
    ratio: float


class Vesting(BaseModel):
    model_config = ConfigDict(extra="forbid")
    schedule: list[VestingPoint] = Field(default_factory=list)
    cliff_month: Optional[int] = None
    forfeiture_rate: Optional[float] = None


class PerfCondition(BaseModel):
    model_config = ConfigDict(extra="forbid")
    metric: Optional[Literal["revenue", "ebitda", "ni", "custom"]] = None
    target: Optional[float] = None
    probability: Optional[float] = None


class MarketCondition(BaseModel):
    model_config = ConfigDict(extra="forbid")
    type: Optional[Literal["target_price", "tsr", "barrier"]] = None
    target_price: Optional[float] = None
    barrier: Optional[float] = None
    knock: Optional[Literal["in", "out"]] = None


class Rights(BaseModel):
    model_config = ConfigDict(extra="forbid")
    conversion: Optional[Conversion] = None
    exchange: Optional[Exchange] = None
    warrant: Optional[Warrant] = None
    redemption: Optional[Redemption] = None
    issuer_call: Optional[IssuerCall] = None
    sale_claim: Optional[SaleClaim] = None
    refixing: Optional[Refixing] = None
    dilution: Optional[Dilution] = None
    vesting: Optional[Vesting] = None
    performance_condition: Optional[PerfCondition] = None
    market_condition: Optional[MarketCondition] = None


class Market(BaseModel):
    model_config = ConfigDict(extra="forbid")
    asset_id: Optional[int] = None
    spot: Optional[float] = None
    volatility: Optional[float] = None
    dividend_yield: Optional[float] = None
    shares_outstanding: Optional[float] = None
    peer_companies: list[dict] = Field(default_factory=list)
    data_source: Optional[str] = None
    as_of: Optional[date] = None


class Curves(BaseModel):
    model_config = ConfigDict(extra="forbid")
    risk_free_curve: CurvePoints
    credit_curve: Optional[CurvePoints] = None
    curve_source: Optional[str] = None
    curve_version: str
    interpolation_method: Literal["linear", "log_linear", "monotone_convex"] = "linear"
    fallback_policy: Literal["nearest_grade", "manual_only", "error"] = "error"


class EngineOptions(BaseModel):
    model_config = ConfigDict(extra="forbid")
    lattice_steps: Optional[int] = None
    simulation_paths: Optional[int] = None
    variance_reduction: Literal["none", "antithetic", "sobol"] = "none"


class ContextMetadata(BaseModel):
    model_config = ConfigDict(extra="forbid")
    issuer: Optional[str] = None
    instrument_name: Optional[str] = None
    requested_by: Optional[int] = None
    created_at: Optional[datetime] = None


class ValuationContext(BaseModel):
    model_config = ConfigDict(extra="forbid")
    instrument_type: InstrumentType
    valuation_date: date
    organization_id: Optional[int] = None
    instrument_id: int
    terms: Terms
    rights: Rights
    market: Market
    curves: Curves
    model: ModelName
    seed: int
    input_hash: str
    model_version: str
    options: EngineOptions = Field(default_factory=EngineOptions)
    metadata: ContextMetadata = Field(default_factory=ContextMetadata)
