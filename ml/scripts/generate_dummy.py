"""설명 기반 지출 분류의 초기 검토용 더미데이터를 생성한다."""

import argparse
import csv
import json
from pathlib import Path


BASE_DIR = Path(__file__).resolve().parents[1]
OUTPUT_DIR = BASE_DIR / "data" / "expense" / "category_dummy_v3"

# 의미가 다른 대표 거래를 그룹으로 묶는다. 같은 그룹의 표현 변형은
# 향후 학습·검증·시험 데이터를 나눌 때 반드시 함께 이동해야 한다.
SEEDS = {
    "SOFTWARE_LICENSE": [
        "영상 편집 프로그램 월 이용료",
        "영상 배경음악 사용 라이선스 구입",
        "자막 제작 소프트웨어 이용권 구매",
        "썸네일 디자인 프로그램 연간 사용권 구매",
        "촬영 자료 백업용 클라우드 저장 서비스 이용료",
        "여행 영상 지도 애니메이션 제작 프로그램 이용료",
        "먹방 영상 자막 편집 소프트웨어 사용권 구매",
        "게임 리뷰 영상 제작용 PC 게임 이용권 구매",
        "일상 브이로그 배경음악 사용 라이선스 구매",
        "제품 리뷰 영상 색보정 프로그램 이용료",
        "영상 편집 앱 월 구독료",
        "동영상 편집 도구 연간 이용권",
        "영상 색보정 프로그램 사용료",
        "자동 자막 생성 서비스 월 이용료",
        "썸네일 제작 디자인 앱 구독료",
        "영상 삽입용 음원 사용권 구매",
        "배경음악 저작권 이용 라이선스 비용",
        "효과음 다운로드 서비스 구독료",
        "게임 리뷰용 스팀 게임 구매",
        "촬영 원본 보관용 클라우드 저장 공간 이용료",
    ],
    "EQUIPMENT": [
        "영상 촬영용 카메라 구매",
        "녹음용 마이크 장비 구입",
        "촬영용 삼각대 구매",
        "영상 편집용 모니터 구매",
        "촬영 조명 장비 구입",
        "여행 영상 촬영용 액션 카메라 구매",
        "먹방 녹음용 핀 마이크 구매",
        "게임 방송용 영상 캡처 장치 구매",
        "일상 영상 촬영용 손떨림 방지 짐벌 구입",
        "리뷰 촬영 대상 무선 마우스 구매",
        "브이로그 촬영용 디지털카메라 구입",
        "야외 촬영용 액션캠 구매",
        "인터뷰 녹음용 무선 마이크 세트 구매",
        "게임 방송용 웹캠 구입",
        "제품 촬영용 LED 조명 구매",
        "영상 편집 작업용 노트북 구매",
        "편집용 고해상도 모니터 구입",
        "촬영 원본 저장용 외장 SSD 구매",
        "리뷰 콘텐츠 제작용 기계식 키보드 구매",
        "작업실 인터넷 연결용 공유기 구매",
    ],
    "TELECOMMUNICATION": [
        "업무용 휴대전화 통신요금 납부",
        "영상 업로드용 인터넷 회선 요금",
        "촬영 현장 모바일 데이터 통신요금",
        "업무 연락용 유심 통신 이용료",
        "실시간 방송용 데이터 회선 사용료",
        "해외 여행 촬영용 데이터 로밍 요금",
        "먹방 라이브 송출용 인터넷 회선 요금",
        "게임 실시간 방송용 유선 인터넷 통신요금",
        "일상 브이로그 업로드용 모바일 데이터 요금",
        "리뷰 영상 업로드용 업무 인터넷 회선 사용료",
        # 검토한 추가 표현이다. 분할 코드의 의미 그룹 대응표를 갱신한 뒤
        # 데이터를 재생성해야 하므로 현재 기존 CSV는 자동으로 바꾸지 않는다.
        "업무 연락용 휴대폰 월정액 요금",
        "촬영 일정 조율에 사용하는 휴대전화 통화료",
        "편집 작업실 유선 인터넷 월 이용료",
        "영상 파일 전송용 광랜 사용료",
        "라이브 방송 작업실 인터넷 회선 요금",
        "야외 방송용 모바일 데이터 추가 이용료",
        "촬영 영상 업로드용 데이터 충전 요금",
        "해외 촬영 기간 휴대전화 로밍 이용료",
        "여행 영상 촬영용 현지 데이터 유심 요금",
        "해외 취재용 eSIM 데이터 이용권",
    ],
    "TRANSPORT": [
        "촬영 장소 이동 택시비",
        "콘텐츠 취재 이동 시외버스 운임",
        "촬영 현장 방문 지하철 운임",
        "지방 촬영 출장 기차 승차권 구매",
        "장비 운반 차량의 촬영장 주차요금",
        "여행 콘텐츠 촬영지 이동 항공권 구매",
        "먹방 촬영 식당 방문 택시 운임",
        "게임 행사 취재 이동 기차 운임",
        "일상 브이로그 촬영 장소 이동 버스비",
        "제품 리뷰 촬영장 방문 지하철 요금",
        "인터뷰 촬영장 방문 택시 요금",
        "촬영 종료 후 장비를 가지고 복귀한 택시비",
        "콘텐츠 취재 장소 이동 시내버스 요금",
        "촬영 일정 이동용 지하철 교통비",
        "지방 촬영 출장 KTX 승차권 구입",
        "지역 축제 취재를 위한 고속버스 운임",
        "여행 영상 촬영지 방문 국내선 항공료",
        "섬 여행 콘텐츠 취재 여객선 승선료",
        "촬영장 방문 차량 주차료",
        "촬영 장비 운반 차량 고속도로 통행료",
    ],
    "SUPPLIES_PROPS": [
        "촬영 배경용 색지 구매",
        "제품 촬영 장식용 조화 소품 구입",
        "촬영 세트 고정용 테이프 구매",
        "카메라 청소용 일회용 클리닝 티슈 구입",
        "촬영 소품 제작용 종이 재료 구매",
        "여행 브이로그 촬영 지도 연출용 종이 구매",
        "먹방 촬영용 음식과 식재료 구매",
        "게임 방송 배경 꾸미기용 장식 소품 구매",
        "일상 영상 촬영용 꽃 장식 소품 구입",
        "리뷰 제품 촬영 배경용 천 구매",
        "제품 촬영 배경지 구매",
        "촬영 세트 장식용 인조 꽃 구입",
        "촬영 배선 고정용 테이프 구매",
        "카메라 렌즈 청소용 일회용 티슈 구입",
        "영상 연출용 종이 장식 재료 구매",
        "먹방 촬영용 배달 음식 주문",
        "요리 콘텐츠 촬영용 채소와 육류 구매",
        "음식 리뷰 촬영용 디저트 구매",
        "게임 방송 배경 장식용 포스터 구입",
        "일상 브이로그 촬영 연출용 풍선 구매",
    ],
    "SPACE_RENTAL": [
        "영상 촬영 스튜디오 시간 대여료",
        "인터뷰 촬영 회의실 대관료",
        "음성 녹음 부스 공간 대여료",
        "요리 영상 촬영용 주방 공간 대관료",
        "콘텐츠 촬영 행사장 대관료",
        "여행 촬영 출장 중 숙소 숙박비",
        "먹방 촬영용 공유 주방 공간 대관료",
        "게임 방송 촬영용 스튜디오 대여료",
        "일상 브이로그 지방 촬영 출장 숙박비",
        "제품 리뷰 촬영용 사진 스튜디오 대관료",
        "제품 촬영용 렌탈 스튜디오 이용료",
        "인터뷰 촬영 회의실 시간 대관료",
        "내레이션 녹음실 대여 비용",
        "먹방 촬영용 공유 주방 이용료",
        "게임 방송 촬영 공간 대여료",
        "영상 촬영용 실내 세트장 대관료",
        "여행 콘텐츠 촬영 출장 호텔 숙박비",
        "지방 취재 일정 중 게스트하우스 숙박료",
        "이틀간의 촬영 출장 펜션 숙박비",
        "일상 브이로그 지방 촬영 숙소 이용료",
    ],
    "OUTSOURCING": [
        "영상 편집 작업 외주 용역비",
        "자막 번역 작업 프리랜서 용역비",
        "썸네일 디자인 제작 외주비",
        "채널 로고 디자인 작업 용역비",
        "영상 음향 보정 작업 외주비",
        "여행 영상 외국어 자막 번역 외주비",
        "먹방 영상 컷 편집 작업 용역비",
        "게임 방송 하이라이트 편집 외주비",
        "일상 브이로그 썸네일 디자인 외주비",
        "제품 리뷰 영상 효과음 편집 외주 용역비",
        "촬영 원본 컷 편집 외주 작업비",
        "영상 자막 삽입 작업 대행료",
        "해외 시청자용 영어 자막 번역 용역비",
        "채널 썸네일 제작 디자이너 작업비",
        "채널 소개 애니메이션 제작 외주비",
        "영상 색보정 전문 작업자 용역비",
        "녹음 파일 잡음 제거 외주 작업비",
        "게임 방송 하이라이트 편집 대행료",
        "먹방 영상 효과음 삽입 작업 외주비",
        "리뷰 영상 오프닝 디자인 제작 용역비",
    ],
    "AD_PROMOTION": [
        "채널 홍보용 검색 광고 집행비",
        "신규 영상 소개 배너 광고 게재료",
        "채널 구독 홍보용 SNS 광고비",
        "영상 콘텐츠 유료 노출 광고비",
        "채널 소개 뉴스레터 광고 게재료",
        "여행 채널 소개 검색 광고 집행비",
        "먹방 신규 영상 홍보 배너 광고비",
        "게임 방송 채널 홍보 SNS 광고 게재료",
        "일상 브이로그 영상 홍보 유료 광고비",
        "제품 리뷰 채널 소개 뉴스레터 광고비",
        "신규 영상 홍보용 검색 광고비",
        "채널 소개 이미지 배너 광고 집행비",
        "여행 영상 홍보 SNS 유료 광고비",
        "먹방 채널 홍보 게시물 광고 게재료",
        "게임 방송 시청자 유입 광고 집행비",
        "일상 브이로그 영상 유료 노출 비용",
        "제품 리뷰 콘텐츠 홍보 광고비",
        "채널 소개 뉴스레터 유료 광고료",
        "영상 홍보용 디스플레이 광고 집행비",
        "채널 구독 유도 캠페인 광고비",
    ],
    "OTHER": [
        "업무 거래 계약서 법률 검토 상담료",
        "업무 장부 기장 대행 수수료",
        "콘텐츠 제작 관련 직무 교육 수강료",
        "채널 사업 운영용 서류 발급 수수료",
        "업무용 장비 파손 보험 가입료",
        "여행 콘텐츠 제작 계약서 법률 검토 상담료",
        "먹방 채널 운영 장부 기장 대행 수수료",
        "게임 콘텐츠 제작 직무 교육 수강료",
        "일상 브이로그 사업 운영용 서류 발급 수수료",
        "리뷰 채널 업무용 촬영 장비 파손 보험료",
        "영상 제작 계약 내용 법률 상담 비용",
        "광고 출연 계약서 변호사 검토 수수료",
        "채널 운영 거래 장부 작성 대행료",
        "업무 회계 기장 서비스 수수료",
        "영상 제작 실무 교육 과정 수강료",
        "콘텐츠 기획 직무 강좌 수강 비용",
        "채널 사업 운영 관련 증명서 발급 비용",
        "업무 제출용 서류 발급 수수료",
        "촬영 장비 파손 보장 보험료",
        "업무용 카메라 보험 가입 비용",
    ],
}

# 단순 접미사 변형으로 늘린 행 수를 독립적인 거래 종류 수로 해석하지 않는다.
TEMPLATES = ("{text}", "{text} 결제", "{text} 비용 지급")
AMBIGUOUS = (
    "물품 구매", "정기 결제", "이용료", "비용 지급", "서비스 결제",
    "온라인 구매", "월 사용료", "구입 대금", "카드 결제", "주문 결제",
)


def build_rows() -> list[dict[str, str]]:
    """정답 검토 전의 생성 샘플을 반환한다. 모델 입력은 description뿐이다."""
    rows = []
    for category, descriptions in SEEDS.items():
        for index, description in enumerate(descriptions, start=1):
            group_id = f"{category.lower()}_{index:02d}"
            for variant, template in enumerate(TEMPLATES, start=1):
                rows.append({
                    "sample_id": f"{group_id}_{variant:02d}",
                    "description": template.format(text=description),
                    "category_code": category,
                    "group_id": group_id,
                    "review_status": "PENDING",
                })
    return rows


def write_csv(path: Path, rows: list[dict[str, str]]) -> None:
    """Excel에서도 한글을 확인할 수 있게 UTF-8 BOM으로 저장한다."""
    with path.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--overwrite", action="store_true",
        help="이미 생성된 파일을 덮어쓴다. 수동 검토 결과도 지워지므로 주의한다.",
    )
    args = parser.parse_args()
    paths = [OUTPUT_DIR / name for name in (
        "labeled_candidates.csv", "needs_info.csv", "manifest.json",
    )]
    if not args.overwrite and any(path.exists() for path in paths):
        parser.error("생성 파일이 이미 있습니다. 재생성하려면 --overwrite를 지정하세요.")

    rows = build_rows()
    if len({row["description"] for row in rows}) != len(rows):
        raise ValueError("중복 설명이 있습니다.")
    if any(not row["description"].strip() for row in rows):
        raise ValueError("빈 설명이 있습니다.")

    # 정보 부족 샘플은 특정 카테고리의 정답으로 학습하지 않고 보류 검증에만 쓴다.
    needs_info = [
        {"sample_id": f"needs_info_{index:02d}", "description": text,
         "expected_status": "NEEDS_INFO"}
        for index, text in enumerate(AMBIGUOUS, start=1)
    ]
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    write_csv(paths[0], rows)
    write_csv(paths[1], needs_info)
    manifest = {
        "dataset_version": "expense_category_dummy_v3",
        "transaction_type": "EXPENSE",
        "source": "synthetic_examples_not_real_transactions",
        "model_input": ["description"],
        "target": "category_code",
        "review_status": "PENDING",
        "labeled_candidate_count": len(rows),
        "independent_seed_group_count": sum(map(len, SEEDS.values())),
        "needs_info_count": len(needs_info),
        "category_counts": {
            category: len(seeds) * len(TEMPLATES)
            for category, seeds in SEEDS.items()
        },
        "split_status": "NOT_SPLIT_REVIEW_SEEDS_FIRST",
    }
    paths[2].write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8",
    )
    group_count = sum(map(len, SEEDS.values()))
    print(f"지출 분류 후보 {len(rows)}건 / 대표 그룹 {group_count}개 / 정보 부족 {len(needs_info)}건")
    print(f"저장 위치: {OUTPUT_DIR}")
    print("대표 샘플과 OTHER 범위를 검토한 뒤 그룹 기준으로 분할하세요. 아직 학습하지 않았습니다.")


if __name__ == "__main__":
    main()
