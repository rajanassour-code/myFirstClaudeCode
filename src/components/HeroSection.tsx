import type { HeroContent } from '@/app/page'

export default function HeroSection({ content }: { content: HeroContent }) {
  return (
    <section id="hero" className="min-h-screen flex items-center bg-gradient-to-br from-gray-50 via-white to-indigo-50 pt-20">
      <div className="max-w-6xl mx-auto px-6 py-24 w-full">
        <div className="flex flex-col md:flex-row items-center gap-12">
          <div className="flex-1 text-center md:text-left">
            {content.availableForWork && (
              <span className="inline-flex items-center gap-2 bg-green-50 text-green-700 text-xs font-semibold px-3 py-1 rounded-full mb-6 border border-green-200">
                <span className="w-2 h-2 rounded-full bg-green-500 animate-pulse" />
                Available for work
              </span>
            )}
            <h1 className="text-5xl md:text-6xl lg:text-7xl font-extrabold text-gray-900 leading-tight mb-4">
              Hi, I&apos;m{' '}
              <span className="text-transparent bg-clip-text bg-gradient-to-r from-indigo-600 to-purple-600">
                {content.name}
              </span>
            </h1>
            <p className="text-2xl md:text-3xl font-semibold text-gray-500 mb-6">{content.title}</p>
            <p className="text-lg text-gray-600 max-w-xl mb-10 leading-relaxed">{content.subtitle}</p>

            <div className="flex flex-wrap gap-4 justify-center md:justify-start">
              <a href={content.ctaPrimary.href}
                className="inline-flex items-center px-6 py-3 bg-indigo-600 text-white font-semibold rounded-xl hover:bg-indigo-700 transition-colors shadow-lg shadow-indigo-200">
                {content.ctaPrimary.label}
              </a>
              <a href={content.ctaSecondary.href}
                className="inline-flex items-center px-6 py-3 bg-white text-gray-900 font-semibold rounded-xl hover:bg-gray-50 transition-colors border border-gray-200 shadow-sm">
                {content.ctaSecondary.label}
              </a>
            </div>
          </div>

          <div className="flex-shrink-0">
            <div className="w-56 h-56 md:w-72 md:h-72 rounded-3xl bg-gradient-to-br from-indigo-400 to-purple-500 shadow-2xl shadow-indigo-200 flex items-center justify-center text-white text-6xl font-bold select-none">
              {content.name.split(' ').map(n => n[0]).join('')}
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
