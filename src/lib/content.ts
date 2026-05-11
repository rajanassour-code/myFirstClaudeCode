import fs from 'fs'
import path from 'path'

const contentDir = path.join(process.cwd(), 'content')

export function readContent<T>(section: string): T {
  const filePath = path.join(contentDir, `${section}.json`)
  const raw = fs.readFileSync(filePath, 'utf-8')
  return JSON.parse(raw) as T
}

export function writeContent(section: string, data: unknown): void {
  const filePath = path.join(contentDir, `${section}.json`)
  fs.writeFileSync(filePath, JSON.stringify(data, null, 2), 'utf-8')
}

export const SECTIONS = ['hero', 'about', 'projects', 'contact', 'nav'] as const
export type Section = typeof SECTIONS[number]
