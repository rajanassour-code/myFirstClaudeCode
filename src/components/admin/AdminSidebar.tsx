'use client'
import Link from 'next/link'
import { usePathname } from 'next/navigation'

const navItems = [
  { label: 'Dashboard', href: '/admin', icon: '🏠' },
  { label: 'Hero', href: '/admin/hero', icon: '✨' },
  { label: 'About', href: '/admin/about', icon: '👤' },
  { label: 'Projects', href: '/admin/projects', icon: '🚀' },
  { label: 'Contact', href: '/admin/contact', icon: '📬' },
  { label: 'Navigation', href: '/admin/nav', icon: '🧭' },
]

export default function AdminSidebar() {
  const pathname = usePathname()

  return (
    <aside className="fixed left-0 top-0 h-full w-64 bg-gray-900 text-white flex flex-col">
      <div className="px-6 py-6 border-b border-gray-800">
        <p className="text-xs text-gray-500 uppercase tracking-widest font-semibold mb-1">Back Office</p>
        <h1 className="text-lg font-bold">Content Manager</h1>
      </div>

      <nav className="flex-1 px-4 py-4 space-y-1">
        {navItems.map((item) => {
          const active = pathname === item.href
          return (
            <Link key={item.href} href={item.href}
              className={`flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-colors ${
                active
                  ? 'bg-indigo-600 text-white'
                  : 'text-gray-400 hover:bg-gray-800 hover:text-white'
              }`}>
              <span>{item.icon}</span>
              {item.label}
            </Link>
          )
        })}
      </nav>

      <div className="px-6 py-4 border-t border-gray-800">
        <Link href="/" target="_blank"
          className="flex items-center gap-2 text-xs text-gray-500 hover:text-gray-300 transition-colors">
          <span>↗</span> View Website
        </Link>
      </div>
    </aside>
  )
}
