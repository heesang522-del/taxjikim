"""수입 설명의 분류와 미분류 기준을 관리한다. PDF 서버 연결은 후속 작업이다."""

import numpy as np


# 지출의 기준을 복사한 임시값. 수입 데이터가 쌓인 뒤 별도로 시험·조정한다.
# (최고 점수 하한, 1위·2위 차이 하한). 하나라도 미달하면 보류한다.
CLASSIFICATION_LIMITS = {
    "logistic_regression": (0.35, 0.15),
    "linear_svc": (0.10, 0.75),
    "setfit": (0.95, 0.20),
}


def classify_descriptions(model: object, model_name: str,
                          descriptions: list[str]) -> list[dict]:
    """설명별 최고 점수와 격차로 카테고리 또는 미분류를 반환한다."""
    minimum_score, minimum_gap = CLASSIFICATION_LIMITS[model_name]
    results = []
    for description in descriptions:
        if not description.strip():
            results.append({"category": "UNCLASSIFIED", "predicted_category": None,
                            "score": None, "gap": None, "reason": "설명 없음"})
            continue
        if model_name == "setfit":
            scores = np.asarray(model.predict_proba([description], as_numpy=True))[0]
            classes = model.labels
        elif model_name == "logistic_regression":
            scores = model.predict_proba([description])[0]
            classes = model.classes_
        else:
            scores = np.asarray(model.decision_function([description]))[0]
            classes = model.classes_
            # 수입 데이터가 두 카테고리만 있을 때도 1·2위 차이를 계산한다.
            if np.ndim(scores) == 0:
                scores = np.array([-float(scores), float(scores)])
        if len(scores) < 2 or not np.isfinite(scores).all():
            raise ValueError("모델의 카테고리별 점수를 확인해주세요.")
        order = np.argsort(scores)
        score = float(scores[order[-1]])
        gap = float(score - scores[order[-2]])
        predicted = str(classes[order[-1]])
        reasons = []
        if score < minimum_score:
            reasons.append("최고 점수 부족")
        if gap < minimum_gap:
            reasons.append("1·2위 차이 부족")
        # OTHER는 정상 분류다. 불확실한 예측만 UNCLASSIFIED로 반환한다.
        results.append({"category": "UNCLASSIFIED" if reasons else predicted,
                        "predicted_category": predicted, "score": score, "gap": gap,
                        "reason": ", ".join(reasons)})
    return results
