import { readContent } from '@/lib/content'
import type { AboutContent } from '@/app/page'
import AboutEditor from './AboutEditor'

export const dynamic = 'force-dynamic'

export default function AboutPage() {
  const data = readContent<AboutContent>('about')
  return <AboutEditor initialData={data} />
}
