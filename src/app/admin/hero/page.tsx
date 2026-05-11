import { readContent } from '@/lib/content'
import type { HeroContent } from '@/app/page'
import HeroEditor from './HeroEditor'

export const dynamic = 'force-dynamic'

export default function HeroPage() {
  const data = readContent<HeroContent>('hero')
  return <HeroEditor initialData={data} />
}
