import { readContent } from '@/lib/content'
import type { ProjectsContent } from '@/app/page'
import ProjectsEditor from './ProjectsEditor'

export const dynamic = 'force-dynamic'

export default function ProjectsPage() {
  const data = readContent<ProjectsContent>('projects')
  return <ProjectsEditor initialData={data} />
}
