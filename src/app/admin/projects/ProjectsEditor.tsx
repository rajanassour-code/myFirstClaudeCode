'use client'
import { useState } from 'react'
import { useContentSave } from '@/hooks/useContentSave'
import SaveBar from '@/components/admin/SaveBar'
import type { ProjectsContent } from '@/app/page'

type Project = ProjectsContent['items'][number]

const inputCls = 'w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 text-sm'

function newProject(): Project {
  return {
    id: Date.now().toString(),
    title: 'New Project',
    description: '',
    tags: [],
    liveUrl: '',
    githubUrl: '',
    image: '',
    featured: false,
  }
}

export default function ProjectsEditor({ initialData }: { initialData: ProjectsContent }) {
  const [data, setData] = useState<ProjectsContent>(initialData)
  const [expanded, setExpanded] = useState<string | null>(null)
  const [tagInputs, setTagInputs] = useState<Record<string, string>>({})
  const { save, saving, saved, error } = useContentSave('projects')

  function updateHeader(field: 'heading' | 'subheading', value: string) {
    setData((prev) => ({ ...prev, [field]: value }))
  }

  function updateProject(id: string, field: keyof Project, value: unknown) {
    setData((prev) => ({
      ...prev,
      items: prev.items.map((p) => p.id === id ? { ...p, [field]: value } : p),
    }))
  }

  function addProject() {
    const p = newProject()
    setData((prev) => ({ ...prev, items: [...prev.items, p] }))
    setExpanded(p.id)
  }

  function removeProject(id: string) {
    setData((prev) => ({ ...prev, items: prev.items.filter((p) => p.id !== id) }))
  }

  function addTag(id: string) {
    const tag = (tagInputs[id] || '').trim()
    if (!tag) return
    const project = data.items.find((p) => p.id === id)!
    updateProject(id, 'tags', [...project.tags, tag])
    setTagInputs((prev) => ({ ...prev, [id]: '' }))
  }

  function removeTag(projectId: string, tagIdx: number) {
    const project = data.items.find((p) => p.id === projectId)!
    updateProject(projectId, 'tags', project.tags.filter((_, i) => i !== tagIdx))
  }

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold text-gray-900 mb-1">Projects Section</h1>
      <p className="text-sm text-gray-500 mb-8">Manage your portfolio projects.</p>

      {/* Header */}
      <div className="bg-white rounded-2xl border border-gray-200 p-6 space-y-4 mb-6">
        <h2 className="font-semibold text-gray-800">Section Header</h2>
        <div>
          <label className="block text-sm font-semibold text-gray-700 mb-1.5">Heading</label>
          <input value={data.heading} onChange={(e) => updateHeader('heading', e.target.value)} className={inputCls} />
        </div>
        <div>
          <label className="block text-sm font-semibold text-gray-700 mb-1.5">Subheading</label>
          <input value={data.subheading} onChange={(e) => updateHeader('subheading', e.target.value)} className={inputCls} />
        </div>
      </div>

      {/* Projects list */}
      <div className="space-y-3 mb-4">
        {data.items.map((project) => (
          <div key={project.id} className="bg-white rounded-2xl border border-gray-200 overflow-hidden">
            {/* Accordion header */}
            <button
              onClick={() => setExpanded(expanded === project.id ? null : project.id)}
              className="w-full flex items-center justify-between px-6 py-4 text-left hover:bg-gray-50 transition-colors">
              <div className="flex items-center gap-3">
                <span className="text-base font-semibold text-gray-900">{project.title}</span>
                {project.featured && <span className="text-xs bg-yellow-50 text-yellow-700 border border-yellow-200 px-2 py-0.5 rounded-full">Featured</span>}
              </div>
              <div className="flex items-center gap-2">
                <button onClick={(e) => { e.stopPropagation(); removeProject(project.id) }}
                  className="text-sm text-red-400 hover:text-red-600 font-medium px-2 py-1 rounded-lg hover:bg-red-50 transition-colors">
                  Delete
                </button>
                <span className="text-gray-400">{expanded === project.id ? '▲' : '▼'}</span>
              </div>
            </button>

            {/* Accordion body */}
            {expanded === project.id && (
              <div className="px-6 pb-6 space-y-4 border-t border-gray-100 pt-4">
                <div>
                  <label className="block text-sm font-semibold text-gray-700 mb-1.5">Title</label>
                  <input value={project.title} onChange={(e) => updateProject(project.id, 'title', e.target.value)} className={inputCls} />
                </div>
                <div>
                  <label className="block text-sm font-semibold text-gray-700 mb-1.5">Description</label>
                  <textarea value={project.description} onChange={(e) => updateProject(project.id, 'description', e.target.value)} rows={3} className={inputCls} />
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-semibold text-gray-700 mb-1.5">Live URL</label>
                    <input value={project.liveUrl} onChange={(e) => updateProject(project.id, 'liveUrl', e.target.value)} placeholder="https://…" className={inputCls} />
                  </div>
                  <div>
                    <label className="block text-sm font-semibold text-gray-700 mb-1.5">GitHub URL</label>
                    <input value={project.githubUrl} onChange={(e) => updateProject(project.id, 'githubUrl', e.target.value)} placeholder="https://github.com/…" className={inputCls} />
                  </div>
                </div>

                {/* Tags */}
                <div>
                  <label className="block text-sm font-semibold text-gray-700 mb-1.5">Tags</label>
                  <div className="flex flex-wrap gap-1.5 mb-2">
                    {project.tags.map((tag, i) => (
                      <span key={i} className="flex items-center gap-1 bg-gray-100 text-gray-700 text-xs px-2 py-1 rounded-lg">
                        {tag}
                        <button onClick={() => removeTag(project.id, i)} className="text-gray-400 hover:text-red-500 font-bold">&times;</button>
                      </span>
                    ))}
                  </div>
                  <div className="flex gap-2">
                    <input value={tagInputs[project.id] || ''}
                      onChange={(e) => setTagInputs((prev) => ({ ...prev, [project.id]: e.target.value }))}
                      onKeyDown={(e) => e.key === 'Enter' && addTag(project.id)}
                      placeholder="Add tag…" className={`${inputCls} flex-1`} />
                    <button onClick={() => addTag(project.id)} className="px-3 py-2.5 bg-indigo-600 text-white text-sm font-semibold rounded-xl hover:bg-indigo-700 transition-colors">Add</button>
                  </div>
                </div>

                <label className="flex items-center gap-3 cursor-pointer">
                  <input type="checkbox" checked={project.featured}
                    onChange={(e) => updateProject(project.id, 'featured', e.target.checked)}
                    className="w-4 h-4 accent-indigo-600" />
                  <span className="text-sm font-medium text-gray-700">Mark as featured</span>
                </label>
              </div>
            )}
          </div>
        ))}
      </div>

      <button onClick={addProject}
        className="w-full py-3 border-2 border-dashed border-gray-300 text-gray-500 rounded-2xl hover:border-indigo-400 hover:text-indigo-600 transition-colors text-sm font-semibold">
        + Add New Project
      </button>

      <SaveBar saving={saving} saved={saved} error={error} onSave={() => save(data)} />
    </div>
  )
}
