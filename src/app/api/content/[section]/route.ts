import { NextRequest, NextResponse } from 'next/server'
import { readContent, writeContent, SECTIONS, type Section } from '@/lib/content'

type Params = { params: { section: string } }

export async function GET(_req: NextRequest, { params }: Params) {
  const section = params.section as Section
  if (!SECTIONS.includes(section)) {
    return NextResponse.json({ error: 'Unknown section' }, { status: 404 })
  }
  try {
    const data = readContent(section)
    return NextResponse.json(data)
  } catch {
    return NextResponse.json({ error: 'Failed to read content' }, { status: 500 })
  }
}

export async function PUT(req: NextRequest, { params }: Params) {
  const section = params.section as Section
  if (!SECTIONS.includes(section)) {
    return NextResponse.json({ error: 'Unknown section' }, { status: 404 })
  }
  try {
    const body = await req.json()
    writeContent(section, body)
    return NextResponse.json({ ok: true })
  } catch {
    return NextResponse.json({ error: 'Failed to write content' }, { status: 500 })
  }
}
