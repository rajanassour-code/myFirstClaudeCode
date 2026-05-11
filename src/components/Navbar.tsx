'use client'
import { useState, useEffect } from 'react'
import type { NavContent } from '@/app/page'

export default function Navbar({ content }: { content: NavContent }) {
  const [scrolled, setScrolled] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 20)
    window.addEventListener('scroll', onScroll)
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  return (
    <nav className={`fixed top-0 left-0 right-0 z-50 transition-all duration-300 ${scrolled ? 'bg-white/90 backdrop-blur-md shadow-sm' : 'bg-transparent'}`}>
      <div className="max-w-6xl mx-auto px-6 py-4 flex items-center justify-between">
        <a href="#" className="text-xl font-bold text-gray-900">{content.logo}</a>

        {/* Desktop nav */}
        <ul className="hidden md:flex items-center gap-8">
          {content.links.map((link) => (
            <li key={link.href}>
              <a href={link.href} className="text-sm font-medium text-gray-600 hover:text-gray-900 transition-colors">
                {link.label}
              </a>
            </li>
          ))}
        </ul>

        {/* Mobile hamburger */}
        <button className="md:hidden p-2" onClick={() => setMenuOpen(!menuOpen)}>
          <span className="sr-only">Menu</span>
          <div className="space-y-1.5">
            <span className={`block w-6 h-0.5 bg-gray-900 transition-transform ${menuOpen ? 'rotate-45 translate-y-2' : ''}`} />
            <span className={`block w-6 h-0.5 bg-gray-900 transition-opacity ${menuOpen ? 'opacity-0' : ''}`} />
            <span className={`block w-6 h-0.5 bg-gray-900 transition-transform ${menuOpen ? '-rotate-45 -translate-y-2' : ''}`} />
          </div>
        </button>
      </div>

      {/* Mobile menu */}
      {menuOpen && (
        <div className="md:hidden bg-white border-t border-gray-100 px-6 py-4">
          {content.links.map((link) => (
            <a key={link.href} href={link.href} onClick={() => setMenuOpen(false)}
              className="block py-3 text-sm font-medium text-gray-700 hover:text-gray-900 border-b border-gray-50 last:border-0">
              {link.label}
            </a>
          ))}
        </div>
      )}
    </nav>
  )
}
