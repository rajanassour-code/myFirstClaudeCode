'use client'
import { useState } from 'react'
import { useContentSave } from '@/hooks/useContentSave'
import SaveBar from '@/components/admin/SaveBar'
import type { NavContent } from '@/app/page'

type NavLink = NavContent['links'][number]

const inputCls = 'w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 text-sm'

export default function NavEditor({ initialData }: { initialData: NavContent }) {
  const [data, setData] = useState<NavContent>(initialData)
  const { save, saving, saved, error } = useContentSave('nav')

  function updateLink(idx: number, field: keyof NavLink, value: string) {
    const updated = data.links.map((l, i) => i === idx ? { ...l, [field]: value } : l)
    setData((prev) => ({ ...prev, links: updated }))
  }

  function addLink() {
    setData((prev) => ({ ...prev, links: [...prev.links, { label: 'New Link', href: '#' }] }))
  }

  function removeLink(idx: number) {
    setData((prev) => ({ ...prev, links: prev.links.filter((_, i) => i !== idx) }))
  }

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold text-gray-900 mb-1">Navigation</h1>
      <p className="text-sm text-gray-500 mb-8">Logo text and navigation links.</p>

      <div className="space-y-6">
        <div className="bg-white rounded-2xl border border-gray-200 p-6">
          <h2 className="font-semibold text-gray-800 mb-4">Logo</h2>
          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-1.5">Logo Text</label>
            <input value={data.logo} onChange={(e) => setData((prev) => ({ ...prev, logo: e.target.value }))} className={inputCls} />
            <p className="text-xs text-gray-400 mt-1.5">Usually your initials, e.g. "RA"</p>
          </div>
        </div>

        <div className="bg-white rounded-2xl border border-gray-200 p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-semibold text-gray-800">Nav Links</h2>
            <button onClick={addLink} className="text-sm text-indigo-600 font-semibold hover:underline">+ Add link</button>
          </div>
          <div className="space-y-3">
            {data.links.map((link, i) => (
              <div key={i} className="flex items-center gap-3">
                <input value={link.label} onChange={(e) => updateLink(i, 'label', e.target.value)}
                  placeholder="Label" className={`${inputCls} w-36 shrink-0`} />
                <input value={link.href} onChange={(e) => updateLink(i, 'href', e.target.value)}
                  placeholder="#section or /page" className={inputCls} />
                <button onClick={() => removeLink(i)} className="text-gray-400 hover:text-red-500 font-bold text-xl leading-none shrink-0">&times;</button>
              </div>
            ))}
          </div>
        </div>
      </div>

      <SaveBar saving={saving} saved={saved} error={error} onSave={() => save(data)} />
    </div>
  )
}
