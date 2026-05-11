import { readContent } from '@/lib/content'
import type { NavContent } from '@/app/page'
import NavEditor from './NavEditor'

export const dynamic = 'force-dynamic'

export default function NavPage() {
  const data = readContent<NavContent>('nav')
  return <NavEditor initialData={data} />
}
