import { readContent } from '@/lib/content'
import type { ContactContent } from '@/app/page'
import ContactEditor from './ContactEditor'

export const dynamic = 'force-dynamic'

export default function ContactPage() {
  const data = readContent<ContactContent>('contact')
  return <ContactEditor initialData={data} />
}
