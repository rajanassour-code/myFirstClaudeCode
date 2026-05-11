import type { AboutContent } from '@/app/page'

export default function AboutSection({ content }: { content: AboutContent }) {
  return (
    <section id="about" className="py-24 bg-white">
      <div className="max-w-6xl mx-auto px-6">
        <h2 className="text-4xl font-bold text-gray-900 mb-16 text-center">{content.heading}</h2>

        <div className="grid md:grid-cols-2 gap-16 items-start">
          {/* Bio */}
          <div>
            <div className="space-y-4">
              {content.bio.split('\n\n').map((para, i) => (
                <p key={i} className="text-gray-600 leading-relaxed text-lg">{para}</p>
              ))}
            </div>
            {content.resumeUrl && (
              <a href={content.resumeUrl} target="_blank" rel="noreferrer"
                className="inline-flex items-center mt-8 px-5 py-2.5 border-2 border-indigo-600 text-indigo-600 font-semibold rounded-xl hover:bg-indigo-600 hover:text-white transition-colors">
                Download Resume
              </a>
            )}
          </div>

          {/* Skills + Stats */}
          <div className="space-y-10">
            <div>
              <h3 className="text-lg font-semibold text-gray-900 mb-4">Technologies I work with</h3>
              <div className="flex flex-wrap gap-2">
                {content.skills.map((skill) => (
                  <span key={skill} className="px-3 py-1.5 bg-indigo-50 text-indigo-700 text-sm font-medium rounded-lg border border-indigo-100">
                    {skill}
                  </span>
                ))}
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              {content.stats.map((stat) => (
                <div key={stat.label} className="bg-gray-50 rounded-2xl p-5 text-center border border-gray-100">
                  <p className="text-3xl font-extrabold text-indigo-600 mb-1">{stat.value}</p>
                  <p className="text-sm text-gray-500 font-medium">{stat.label}</p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
