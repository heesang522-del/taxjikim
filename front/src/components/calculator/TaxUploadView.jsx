import FileUploader from './FileUploader';

// 기존 진입점도 통합 업로더를 사용해 하드코딩된 회원 번호나 중복 업로드 코드를 없앤다.
export default function TaxCalculatorView() {
  return <FileUploader />;
}
