평가보고서 PDF 한글 폰트 (5-8)
================================

ReportPdfBuilder 는 한글 렌더링에 다음 순서로 폰트를 사용합니다:

1) /fonts/NotoSansKR-Regular.ttf  (이 폴더에 두면 ★임베딩)  ← 권장
2) 없으면 OpenPDF 내장 한글 CJK 폰트(HYGoThic-Medium / UniKS-UCS2-H) 폴백
   - 임베딩은 아니나 대부분의 뷰어에서 한글이 정상 표시됩니다.

■ 완전 임베딩(권장) 설정
  - Noto Sans KR(OFL 라이선스, 상업적 배포 가능)를 내려받아
    이 폴더에 `NotoSansKR-Regular.ttf` 파일명으로 저장하면 자동 임베딩됩니다.
  - 출처: https://fonts.google.com/noto/specimen/Noto+Sans+KR (OFL)
  - OFL 폰트이므로 저장소에 함께 커밋 가능합니다.

■ 주의
  - 바이너리 폰트 파일은 코드 생성 도구로 만들 수 없어 이 안내만 포함합니다.
  - 폰트 미배치 시에도 (2)의 폴백으로 보고서는 생성되며 한글이 표시됩니다.
