import type { ProjectsContent } from '@/app/page'

export default function ProjectsSection({ content }: { content: ProjectsContent }) {
  return (
    <section id="projects" className="py-24 bg-gray-50">
      <div className="max-w-6xl mx-auto px-6">
        <div className="text-center mb-16">
          <h2 className="text-4xl font-bold text-gray-900 mb-4">{content.heading}</h2>
          <p className="text-lg text-gray-500">{content.subheading}</p>
        </div>

        <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
          {content.items.map((project) => (
            <div key={project.id}
              className="bg-white rounded-2xl border border-gray-100 shadow-sm hover:shadow-md transition-shadow overflow-hidden group flex flex-col">

              {/* Color block placeholder for image */}
              <div className="h-44 bg-gradient-to-br from-indigo-100 to-purple-100 flex items-center justify-center">
                <span className="text-4xl font-bold text-indigo-300">{project.title[0]}</span>
              </div>

              <div className="p-6 flex flex-col flex-1">
                <div className="flex items-start justify-between mb-3">
                  <h3 className="text-lg font-bold text-gray-900">{project.title}</h3>
                  {project.featured && (
                    <span className="text-xs font-semibold bg-yellow-50 text-yellow-700 border border-yellow-200 px-2 py-0.5 rounded-full ml-2 shrink-0">
                      Featured
                    </span>
                  )}
                </div>
                <p className="text-sm text-gray-500 leading-relaxed mb-4 flex-1">{project.description}</p>

                <div className="flex flex-wrap gap-1.5 mb-5">
                  {project.tags.map((tag) => (
                    <span key={tag} className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded-md font-medium">
                      {tag}
                    </span>
                  ))}
                </div>

                <div className="flex gap-3">
                  {project.liveUrl && (
                    <a href={project.liveUrl} target="_blank" rel="noreferrer"
                      className="flex-1 text-center text-sm font-semibold py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors">
                      Live Demo
                    </a>
                  )}
                  {project.githubUrl && (
                    <a href={project.githubUrl} target="_blank" rel="noreferrer"
                      className="flex-1 text-center text-sm font-semibold py-2 border border-gray-200 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors">
                      GitHub
                    </a>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
