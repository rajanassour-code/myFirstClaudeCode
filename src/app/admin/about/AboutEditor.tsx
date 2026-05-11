'use client'
import { useState } from 'react'
import { useContentSave } from '@/hooks/useContentSave'
import SaveBar from '@/components/admin/SaveBar'
import type { AboutContent } from '@/app/page'

const inputCls = 'w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 text-sm'

export default function AboutEditor({ initialData }: { initialData: AboutContent }) {
  const [data, setData] = useState<AboutContent>(initialData)
  const { save, saving, saved, error } = useContentSave('about')
  const [newSkill, setNewSkill] = useState('')

  function set<K extends keyof AboutContent>(field: K, value: AboutContent[K]) {
    setData((prev) => ({ ...prev, [field]: value }))
  }

  function addSkill() {
    if (!newSkill.trim()) return
    set('skills', [...data.skills, newSkill.trim()])
    setNewSkill('')
  }

  function removeSkill(idx: number) {
    set('skills', data.skills.filter((_, i) => i !== idx))
  }

  function updateStat(idx: number, field: 'label' | 'value', value: string) {
    const updated = data.stats.map((s, i) => i === idx ? { ...s, [field]: value } : s)
    set('stats', updated)
  }

  function addStat() {
    set('stats', [...data.stats, { label: 'New Stat', value: '0' }])
  }

  function removeStat(idx: number) {
    set('stats', data.stats.filter((_, i) => i !== idx))
  }

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold text-gray-900 mb-1">About Section</h1>
      <p className="text-sm text-gray-500 mb-8">Your bio, skills, and stats.</p>

      <div className="space-y-6">
        {/* Bio */}
        <div className="bg-white rounded-2xl border border-gray-200 p-6 space-y-4">
          <h2 className="font-semibold text-gray-800">Bio</h2>

          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-1.5">Section Heading</label>
            <input value={data.heading} onChange={(e) => set('heading', e.target.value)} className={inputCls} />
          </div>

          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-1.5">Bio Text</label>
            <p className="text-xs text-gray-400 mb-2">Separate paragraphs with a blank line.</p>
            <textarea value={data.bio} onChange={(e) => set('bio', e.target.value)} rows={6} className={inputCls} />
          </div>

          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-1.5">Resume URL</label>
            <input value={data.resumeUrl} onChange={(e) => set('resumeUrl', e.target.value)} placeholder="/resume.pdf" className={inputCls} />
          </div>
        </div>

        {/* Skills */}
        <div className="bg-white rounded-2xl border border-gray-200 p-6">
          <h2 className="font-semibold text-gray-800 mb-4">Skills / Technologies</h2>
          <div className="flex flex-wrap gap-2 mb-4">
            {data.skills.map((skill, i) => (
              <span key={i} className="flex items-center gap-1 bg-indigo-50 text-indigo-700 text-sm px-3 py-1 rounded-lg border border-indigo-100">
                {skill}
                <button onClick={() => removeSkill(i)} className="ml-1 text-indigo-400 hover:text-red-500 font-bold leading-none">&times;</button>
              </span>
            ))}
          </div>
          <div className="flex gap-2">
            <input value={newSkill} onChange={(e) => setNewSkill(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && addSkill()}
              placeholder="Add skill…" className={`${inputCls} flex-1`} />
            <button onClick={addSkill} className="px-4 py-2.5 bg-indigo-600 text-white text-sm font-semibold rounded-xl hover:bg-indigo-700 transition-colors">
              Add
            </button>
          </div>
        </div>

        {/* Stats */}
        <div className="bg-white rounded-2xl border border-gray-200 p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-semibold text-gray-800">Stats</h2>
            <button onClick={addStat} className="text-sm text-indigo-600 font-semibold hover:underline">+ Add stat</button>
          </div>
          <div className="space-y-3">
            {data.stats.map((stat, i) => (
              <div key={i} className="flex items-center gap-3">
                <input value={stat.value} onChange={(e) => updateStat(i, 'value', e.target.value)}
                  placeholder="Value (e.g. 5+)" className={`${inputCls} w-28`} />
                <input value={stat.label} onChange={(e) => updateStat(i, 'label', e.target.value)}
                  placeholder="Label" className={inputCls} />
                <button onClick={() => removeStat(i)} className="text-gray-400 hover:text-red-500 font-bold text-lg leading-none shrink-0">&times;</button>
              </div>
            ))}
          </div>
        </div>
      </div>

      <SaveBar saving={saving} saved={saved} error={error} onSave={() => save(data)} />
    </div>
  )
}
