// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useNavigate } from 'react-router-dom';
import ProfileForm from '../components/calculator/ProfileForm';
import FileUploader from '../components/calculator/FileUploader';
import EntryForm from '../components/calculator/EntryForm';
import EntryTable from '../components/calculator/EntryTable';
import SummaryRail from '../components/calculator/SummaryRail';
import { ROUTES } from '../utils/routes';
import { Card, Segmented } from '../components/common/index';
import { useTax } from '../store/useTaxStore';

export default function CalcInputPage() {
  const navigate = useNavigate();
  const { inputMethod, setInputMethod } = useTax();
  return (
    <div className="grid g-side">
      <div className="grid" style={{ minWidth: 0 }}>
        <ProfileForm />
        <Card title="입력 방식 선택" icon="fileText">
          <Segmented label="입력 방식" value={inputMethod} onChange={setInputMethod} options={[
            { value: 'file', label: 'PDF 불러와서 계산' },
            { value: 'manual', label: '직접 입력해서 분석' },
          ]} />
          <p className="small muted" style={{ marginTop: 10 }}>선택한 방식의 내역만 계산합니다. 다른 방식으로 전환해도 기존 내역은 유지됩니다.</p>
        </Card>
        {inputMethod === 'file' ? <FileUploader /> : <EntryForm />}
        <EntryTable />
      </div>
      <SummaryRail onCalculate={() => navigate(ROUTES.calcLoading)} />
    </div>
  );
}
