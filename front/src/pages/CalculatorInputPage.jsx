// taxlab 코드는 React Compiler 없이 동작하게 만들어져 있어 이 파일은 컴파일러 대상에서 뺀다
'use no memo';

import { useNavigate } from 'react-router-dom';
import ProfileForm from '../components/calculator/ProfileForm';
import FileDropzone from '../components/calculator/FileDropzone';
import ImportReview from '../components/calculator/ImportReview';
import EntryForm from '../components/calculator/EntryForm';
import EntryTable from '../components/calculator/EntryTable';
import SummaryRail from '../components/calculator/SummaryRail';
import { ROUTES } from '../utils/routes';

export default function CalcInputPage() {
  const navigate = useNavigate();
  return (
    <div className="grid g-side">
      <div className="grid" style={{ minWidth: 0 }}>
        <ProfileForm />
        <FileDropzone />
        <ImportReview />
        <EntryForm />
        <EntryTable />
      </div>
      <SummaryRail onCalculate={() => navigate(ROUTES.calcLoading)} />
    </div>
  );
}
