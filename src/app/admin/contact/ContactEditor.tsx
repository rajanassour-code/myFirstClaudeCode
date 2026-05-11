'use client'
import { useState } from 'react'
import { useContentSave } from '@/hooks/useContentSave'
import SaveBar from '@/components/admin/SaveBar'
import type { ContactContent } from '@/app/page'

type Social = ContactContent['socials'][number]

const inputCls = 'w-full px-4 py-2.5 rounded-xl border border-gray-200 focus:outline-none focus:ring-2 focus:ring-indigo-500 text-sm'

export default function ContactEditor({ initialData }: { initialData: ContactContent }) {
  const [data, setData] = useState<ContactContent>(initialData)
  const { save, saving, saved, error } = useContentSave('contact')

  function set<K extends keyof ContactContent>(field: K, value: ContactContent[K]) {
    setData((prev) => ({ ...prev, [field]: value }))
  }

  function updateSocial(idx: number, field: keyof Social, value: string) {
    const updated = data.socials.map((s, i) => i === idx ? { ...s, [field]: value } : s)
    set('socials', updated)
  }

  function addSocial() {
    set('socials', [...data.socials, { platform: 'Platform', url: 'https://', username: '@handle' }])
  }

  function removeSocial(idx: number) {
    set('socials', data.socials.filter((_, i) => i !== idx))
  }

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-bold text-gray-900 mb-1">Contact Section</h1>
      <p className="text-sm text-gray-500 mb-8">Your contact info and social links.</p>

      <div className="space-y-6">
        {/* Main info */}
        <div className="bg-white rounded-2xl border border-gray-200 p-6 space-y-4">
          <h2 className="font-semibold text-gray-800">Content</h2>

          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-1.5">Section Heading</label>
            <input value={data.heading} onChange={(e) => set('heading', e.target.value)} className={inputCls} />
          </div>

          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-1.5">Subheading</label>
            <input value={data.subheading} onChange={(e) => set('subheading', e.target.value)} className={inputCls} />
          </div>
        </div>

        {/* Contact details */}
        <div className="bg-white rounded-2xl border border-gray-200 p-6 space-y-4">
          <h2 className="font-semibold text-gray-800">Contact Details</h2>

          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-1.5">Email</label>
            <input type="email" value={data.email} onChange={(e) => set('email', e.target.value)} className={inputCls} />
          </div>

          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-1.5">Phone</label>
            <input value={data.phone} onChange={(e) => set('phone', e.target.value)} className={inputCls} />
          </div>

          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-1.5">Location</label>
            <input value={data.location} onChange={(e) => set('location', e.target.value)} placeholder="City, Country" className={inputCls} />
          </div>

          <label className="flex items-center gap-3 cursor-pointer">
            <input type="checkbox" checked={data.formEnabled}
              onChange={(e) => set('formEnabled', e.target.checked)}
              className="w-4 h-4 accent-indigo-600" />
            <span className="text-sm font-medium text-gray-700">Show contact form</span>
          </label>
        </div>

        {/* Social links */}
        <div className="bg-white rounded-2xl border border-gray-200 p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-semibold text-gray-800">Social Links</h2>
            <button onClick={addSocial} className="text-sm text-indigo-600 font-semibold hover:underline">+ Add</button>
          </div>
          <div className="space-y-3">
            {data.socials.map((social, i) => (
              <div key={i} className="flex items-center gap-2">
                <input value={social.platform} onChange={(e) => updateSocial(i, 'platform', e.target.value)}
                  placeholder="Platform" className={`${inputCls} w-28 shrink-0`} />
                <input value={social.url} onChange={(e) => updateSocial(i, 'url', e.target.value)}
                  placeholder="https://…" className={inputCls} />
                <input value={social.username} onChange={(e) => updateSocial(i, 'username', e.target.value)}
                  placeholder="@handle" className={`${inputCls} w-32 shrink-0`} />
                <button onClick={() => removeSocial(i)} className="text-gray-400 hover:text-red-500 font-bold text-xl leading-none shrink-0">&times;</button>
              </div>
            ))}
          </div>
        </div>
      </div>

      <SaveBar saving={saving} saved={saved} error={error} onSave={() => save(data)} />
    </div>
  )
}
