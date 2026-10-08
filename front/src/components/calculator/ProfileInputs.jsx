import { Field, Grid, Input, Select, Stack, Hint } from '../ui'

// 나이 / 지역 / 직군 입력. 값은 화면에만 저장되고 세액 계산에는 쓰이지 않는다
export default function ProfileInputs({ profile, onChange, regions, jobs }) {
  const set = (key) => (e) => onChange({ ...profile, [key]: e.target.value })

  return (
    <Stack $gap={8}>
      <Grid $cols={3} $gap={16}>
        <Field>
          <span>나이</span>
          <Input type="number" value={profile.age} onChange={set('age')} />
        </Field>
        <Field>
          <span>지역</span>
          <Select value={profile.region} onChange={set('region')}>
            {regions.map((r) => (
              <option key={r}>{r}</option>
            ))}
          </Select>
        </Field>
        <Field>
          <span>직군 카테고리</span>
          <Select value={profile.job} onChange={set('job')}>
            {jobs.map((j) => (
              <option key={j}>{j}</option>
            ))}
          </Select>
        </Field>
      </Grid>
      <Hint>나이·지역·직군은 아직 세액 계산에 반영되지 않습니다.</Hint>
    </Stack>
  )
}
