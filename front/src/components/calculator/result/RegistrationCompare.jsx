import { Button, Card, Icon } from '../../common/index';

const NTS_GUIDE = 'https://www.nts.go.kr/nts/cm/cntnts/cntntsView.do?cntntsId=7802&mi=2480';

export default function RegistrationCompare({ businessRegistered }) {
  // 저장된 등록 여부에 따라 지금 확인할 사항을 보여준다.
  if (typeof businessRegistered !== 'boolean') return null;

  return (
    <Card title={businessRegistered ? '사업 운영·확장 안내' : '사업자등록 안내'} icon="scale">
      {businessRegistered ? (
        <>
          <p>
            플랫폼 정산서와 사업 지출 증빙을 보관하고, 면세사업자의 사업장현황신고와 종합소득세 신고 일정을 확인하세요.
          </p>
          <p>
            <strong>사업을 확장할 때</strong> 직원을 고용하거나 사업용 촬영 시설을 갖추면 부가가치세 과세 대상에
            해당하는지 확인하세요. 과세 대상이면 부가가치세를 별도로 신고·계산해야 합니다.
          </p>
          <p>
            <strong>창업 세액감면을 알아보고 있다면</strong> 새 사업을 시작한 것으로 인정되는지, 감면 대상 업종과
            중소기업 요건에 해당하는지 확인하세요. 기존 사업을 확장하거나 외주를 맡기거나 과세사업자로 전환하는
            것만으로는 감면이 적용되지 않습니다. 이 화면의 예상세액에는 창업 세액감면을 반영하지 않았습니다.
          </p>
        </>
      ) : (
        <>
          <p>
            <strong>이미 수익 활동을 시작했는데 아직 등록하지 않았다면</strong> 내 활동에 맞는 업종과 신청 절차를 확인해
            보세요. 사업자등록은 시작 전에도 신청할 수 있고, 이미 시작했다면 사업 시작일부터 20일 이내에 신청하는 게
            원칙이에요.
          </p>
          <p>
            <strong>어떤 업종으로 신청하나요?</strong> 직원과 사업용 시설 없이 활동한다면 면세사업자 등록을 확인하세요.
            직원을 고용하거나 사업용 스튜디오를 갖추고 운영한다면 부가가치세 과세 대상인지 확인하세요.
          </p>
          <p>
            <strong>올려주신 지출 내역</strong> 예상세액은 입력한 지출을 사업 경비로 가정해 계산했어요. 실제 신고를
            준비할 때는 사업에 쓴 비용인지 확인하고 관련 영수증과 플랫폼 정산서를 챙겨두세요.
          </p>
          <p>
            <strong>등록 신청 준비</strong> 국세청 안내에서 내 업종과 준비 서류를 확인한 뒤 홈택스에서 신청할 수 있어요.
            사업장을 임차했다면 임대차계약서도 준비하세요.
          </p>
          <p>
            <strong>창업 세액감면도 확인해 보세요</strong> 새로 사업을 시작한다면 업종과 창업 시기·지역이 감면 요건에
            맞는지 살펴보세요. 이미 수익 활동을 해왔다면 첫 사업자등록이라도 세법상 새 창업으로 인정되는지 확인이
            필요해요.
          </p>
        </>
      )}
      <div className="row" style={{ marginTop: 12 }}>
        <Button size="sm" variant="soft" href={NTS_GUIDE}>
          국세청 1인 미디어 세무 안내 <Icon name="externalLink" size={14} />
        </Button>
        <Button
          size="sm"
          variant="soft"
          href="https://www.law.go.kr/LSW/lsSideInfoP.do?docCls=jo&joBrNo=00&joNo=0006&lsiSeq=285907&urlMode=lsScJoRltInfoR"
        >
          창업 세액감면 요건 확인 <Icon name="externalLink" size={14} />
        </Button>
        {!businessRegistered && (
          <Button size="sm" variant="soft" href="https://www.hometax.go.kr">
            홈택스에서 사업자등록 신청 <Icon name="externalLink" size={14} />
          </Button>
        )}
      </div>
    </Card>
  );
}
