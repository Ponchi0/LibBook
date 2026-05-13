'use strict';

const path = require('path');
const express = require('express');
const cors = require('cors');
const Database = require('better-sqlite3');
const mammoth = require('mammoth');
const WordExtractor = require('word-extractor');

const PORT = process.env.PORT || 3000;
const DB_PATH = process.env.DB_PATH || path.join(__dirname, 'data.db');

const db = new Database(DB_PATH);
db.pragma('journal_mode = WAL');

function ensureBooksColumns() {
  const cols = db.prepare('PRAGMA table_info(books)').all();
  const names = cols.map((c) => c.name);
  if (names.length === 0) {
    db.exec(`
      CREATE TABLE books (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT,
        description TEXT,
        icon TEXT,
        tags TEXT,
        text TEXT
      );
    `);
    return;
  }
  if (!names.includes('tags')) {
    db.exec('ALTER TABLE books ADD COLUMN tags TEXT');
  }
  if (!names.includes('text')) {
    db.exec('ALTER TABLE books ADD COLUMN text TEXT');
  }
}

function ensureUsersTable() {
  db.exec(`
    CREATE TABLE IF NOT EXISTS users (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      email TEXT UNIQUE,
      name TEXT,
      password TEXT,
      icon TEXT
    );
  `);
}

function initDb() {
  ensureUsersTable();
  ensureBooksColumns();
}

function parseTags(value) {
  if (value == null || value === '') return [];
  try {
    const arr = JSON.parse(value);
    return Array.isArray(arr) ? arr.filter((t) => typeof t === 'string') : [];
  } catch {
    return [];
  }
}

function rowToBook(row) {
  if (!row) return null;
  return {
    id: row.id,
    name: row.name,
    description: row.description,
    icon: row.icon,
    tags: parseTags(row.tags),
    text: row.text,
  };
}

initDb();

const app = express();
app.use(cors());
app.use(express.json({ limit: '20mb' }));

/** ---------- Books (tags column: JSON array string in SQLite) ---------- */

app.get('/books', (req, res) => {
  const rows = db.prepare('SELECT id, name, description, icon, tags, text FROM books ORDER BY id').all();
  res.json(rows.map(rowToBook));
});

app.get('/books/:id', (req, res) => {
  const id = Number(req.params.id);
  const row = db.prepare('SELECT id, name, description, icon, tags, text FROM books WHERE id = ?').get(id);
  if (!row) return res.status(404).json({ error: 'Book not found' });
  res.json(rowToBook(row));
});

async function resolveBookText(body) {
  if (!body) return null;
  if (typeof body.text === 'string') {
    return body.text;
  }
  const b64 = typeof body.textFileBase64 === 'string' ? body.textFileBase64 : null;
  if (!b64) return null;
  const mime = typeof body.textFileMime === 'string' ? body.textFileMime : '';
  const buf = Buffer.from(b64, 'base64');

  if (mime === 'text/plain') {
    return buf.toString('utf8');
  }
  if (mime === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document') {
    const result = await mammoth.extractRawText({ buffer: buf });
    return result && typeof result.value === 'string' ? result.value : '';
  }
  if (mime === 'application/msword') {
    const extractor = new WordExtractor();
    const doc = await extractor.extract(buf);
    return doc && typeof doc.getBody === 'function' ? doc.getBody() : '';
  }
  return null;
}

app.post('/books', async (req, res) => {
  const { name, description, icon, tags } = req.body || {};
  const text = await resolveBookText(req.body);
  const tagsJson = JSON.stringify(Array.isArray(tags) ? tags : []);
  const info = db
    .prepare(
      'INSERT INTO books (name, description, icon, tags, text) VALUES (@name, @description, @icon, @tags, @text)',
    )
    .run({
      name: name ?? null,
      description: description ?? null,
      icon: icon ?? null,
      tags: tagsJson,
      text: text ?? null,
    });
  const row = db
    .prepare('SELECT id, name, description, icon, tags, text FROM books WHERE id = ?')
    .get(info.lastInsertRowid);
  res.status(201).json(rowToBook(row));
});

app.put('/books/:id', async (req, res) => {
  const id = Number(req.params.id);
  const existing = db.prepare('SELECT id FROM books WHERE id = ?').get(id);
  if (!existing) return res.status(404).json({ error: 'Book not found' });
  const { name, description, icon, tags } = req.body || {};
  const incomingText = await resolveBookText(req.body);
  const cur = db.prepare('SELECT name, description, icon, tags, text FROM books WHERE id = ?').get(id);
  const next = {
    name: name !== undefined ? name : cur.name,
    description: description !== undefined ? description : cur.description,
    icon: icon !== undefined ? icon : cur.icon,
    tags:
      tags !== undefined
        ? JSON.stringify(Array.isArray(tags) ? tags : [])
        : cur.tags,
    text: incomingText !== null && incomingText !== undefined ? incomingText : cur.text,
  };
  db.prepare(
    'UPDATE books SET name = @name, description = @description, icon = @icon, tags = @tags, text = @text WHERE id = @id',
  ).run({ ...next, id });
  const row = db.prepare('SELECT id, name, description, icon, tags, text FROM books WHERE id = ?').get(id);
  res.json(rowToBook(row));
});

app.delete('/books/:id', (req, res) => {
  const id = Number(req.params.id);
  const info = db.prepare('DELETE FROM books WHERE id = ?').run(id);
  if (info.changes === 0) return res.status(404).json({ error: 'Book not found' });
  res.status(204).end();
});

app.listen(PORT, () => {
  console.log(`SamsungApiServer listening on http://localhost:${PORT}`);
});
