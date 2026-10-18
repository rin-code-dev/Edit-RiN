#!/usr/bin/env python3
"""Render the static, progressively enhanced manual. Run --check in CI."""
import argparse
import html
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTENT = ROOT / 'scripts/guide-content.json'
ICONS = ROOT / 'website/assets/guide/app-icons.svg'

def esc(value):
    return html.escape(str(value), quote=True)

def bi(pair):
    return f'<span lang="ja">{esc(pair[0])}</span><span lang="en">{esc(pair[1])}</span>'

def aria(ja, en):
    return f'aria-label="{esc(ja)}" data-aria-ja="{esc(ja)}" data-aria-en="{esc(en)}"'

def button(action, ja, en, cls=''):
    return f'<button type="button" data-action="{action}" class="{cls}">{bi([ja, en])}</button>'

def inline_icons():
    raw = ICONS.read_text()
    raw = raw.replace('<svg xmlns="http://www.w3.org/2000/svg">', '<svg xmlns="http://www.w3.org/2000/svg" hidden aria-hidden="true">', 1)
    return raw.replace('<symbol id="', '<symbol id="app-icon-').strip()

def render(data):
    chapters = data['chapters']
    titles = {t['id']: t['title'] for c in chapters for t in c['topics']}
    out = ['''<!doctype html>
<html lang="ja">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="theme-color" content="#f7f8f5">
  <title>Edit:RiN 詳細マニュアル / User manual</title>
  <meta name="description" content="Edit:RiNの詳細マニュアル。作品作成、コード編集、パラメーター、素材、保存、出力を、操作図と実行できるコード例で説明します。">
  <meta property="og:type" content="article">
  <meta property="og:title" content="Edit:RiN 詳細マニュアル / User manual">
  <meta property="og:description" content="操作手順・コード例・実行デモで調べる、Edit:RiNの全機能。Detailed instructions and interactive examples for Edit:RiN.">
  <meta property="og:url" content="https://rin-code-dev.github.io/Edit-RiN/guide.html">
  <link rel="canonical" href="https://rin-code-dev.github.io/Edit-RiN/guide.html">
  <link rel="icon" href="assets/icon.png">
  <link rel="stylesheet" href="guide.css">
  <link rel="stylesheet" href="guide-app-ui.css">
  <script src="guide.js" defer></script>
  <script src="guide-app-ui.js" defer></script>
  <script src="guide-demos.js" defer></script>
</head>
<body>
''']
    out += [inline_icons(),
            f'<a class="skip" href="#content">{bi(["本文へ移動", "Skip to content"])}</a>',
            '<header class="topbar">',
            '<a class="brand" href="index.html"><img src="assets/icon.png" width="32" height="32" alt=""><span>Edit:RiN</span></a>',
            f'<span class="header-label">{bi(["使い方マニュアル", "User manual"])}</span>',
            f'<span class="mobile-current-chapter enhanced" id="mobile-current-chapter" aria-hidden="true">01</span>',
            f'<div class="language enhanced" role="group" {aria("言語選択", "Language")}><button type="button" data-lang="ja" aria-pressed="true">日本語</button><button type="button" data-lang="en" aria-pressed="false">EN</button></div>',
            f'<button id="menu-toggle" type="button" class="mobile-menu enhanced" aria-expanded="false" aria-controls="sidebar" {aria("メニューと目次", "Menu and contents")}><span class="hamburger-icon" aria-hidden="true"><span></span><span></span><span></span></span><span class="hamburger-text">{bi(["目次", "Menu"])}</span></button>',
            '</header><div class="workspace">',
            '<aside class="sidebar" id="sidebar">',
            f'<p class="nav-label">{bi(["マニュアル", "MANUAL"])}</p>',
            f'<nav {aria("全体目次", "Manual contents")} class="chapter-nav">']
    for n, c in enumerate(chapters, 1):
        out.append(f'<a href="#{c["id"]}" data-chapter-link="{c["id"]}"><span class="nav-number">{n:02}</span><span>{bi(c["title"])}</span><span class="chapter-chevron enhanced" aria-hidden="true">▾</span></a>')
    out += ['</nav>',
            f'<div class="sidebar-foot"><span class="version">v{esc(data["version"])}</span><p>{bi(["Androidアプリの操作を説明します。", "Instructions for the Android app."])}</p><a href="index.html">{bi(["公式サイトへ", "Visit the website"])} ↗</a></div>',
            '</aside><main id="content" tabindex="-1">',
            f'<section class="search-area enhanced" {aria("検索", "Search")}>',
            f'<label class="search-label" for="guide-search">{bi(["機能を調べる", "Find a feature"])}</label>',
            f'<div class="search-box"><span aria-hidden="true">⌕</span><input id="guide-search" type="search" autocomplete="off" placeholder="機能名・API・困っていること"><button type="button" id="clear-search">{bi(["クリア", "Clear"])}</button></div>',
            '<div id="search-results" hidden><p id="search-status" role="status"></p><div id="result-list"></div></div>',
            '</section>',
            '<div class="manual-intro">',
            f'<p class="eyebrow">EDIT:RiN / DOCUMENTATION</p><h1>{bi(["使い方マニュアル", "User manual"])}</h1>',
            f'<p>{bi(["操作の場所、手順、コード例を機能ごとにまとめています。目次から章を選ぶか、機能名で検索してください。", "Find controls, step-by-step instructions, and code examples for each feature. Choose a chapter or search for a feature."])}</p>',
            '</div>']
    for n, c in enumerate(chapters, 1):
        out += [f'<section class="chapter" id="{c["id"]}" data-chapter>',
                f'<header class="chapter-heading"><p class="eyebrow">{n:02} / {len(chapters):02}</p><h2 tabindex="-1">{bi(c["title"])}</h2><p>{bi(c["summary"])}</p></header>',
                f'<nav class="inline-toc" {aria("章内目次", "In this chapter")}>']
        for t in c['topics']:
            out.append(f'<a href="#{t["id"]}">{bi(t["title"])}</a>')
        out.append('</nav>')
        if n == 1:
            out += ['<figure class="screen-figure"><div class="annotated-screen"><img src="assets/demos/code-preview.jpg" width="480" height="986" alt="Edit:RiNの画面例 / Example Edit:RiN screen" loading="lazy">',
                    '<span class="pin pin-title" aria-hidden="true">1</span><span class="pin pin-settings" aria-hidden="true">2</span><span class="pin pin-preview" aria-hidden="true">3</span><span class="pin pin-editor" aria-hidden="true">4</span></div>',
                    f'<figcaption><p class="eyebrow">{bi(["画面の読み方", "SCREEN MAP"])}</p><h3>{bi(["作品とコードを同じ画面で", "Artwork and code together"])}</h3><ol class="screen-key">',
                    f'<li>{bi(["作品名：作品一覧を開く", "Work title: open the work picker"])}</li>',
                    f'<li>{bi(["歯車：アプリの設定", "Gear: app settings"])}</li>',
                    f'<li>{bi(["プレビュー操作：パラメーター・出力など", "Preview controls: parameters, export, and more"])}</li>',
                    f'<li>{bi(["コード欄：ソースの閲覧と編集", "Editor: read and edit source"])}</li></ol>',
                    f'<p class="figure-note">{bi(["画面例。版、言語、画面の向きや設定で配置は変わります。", "Example screen. Layout varies with version, language, orientation, and settings."])}</p></figcaption></figure>']
        else:
            # Native HTML diagram remains readable with scripts disabled.
            out += [f'<figure class="flow-figure"><figcaption>{bi(["操作の流れ（説明図）", "Workflow diagram"])}</figcaption><ol>']
            flow = {
                'editor': [('code', 'コードを選ぶ', 'Choose code'), ('search', '編集・確認', 'Edit & inspect'), ('play', '実行する', 'Run changes')],
                'preview-pip': [('code', '変更する', 'Make a change'), ('play', '実行する', 'Run'), ('pip_resize', '結果を確認', 'Inspect output')],
                'parameters': [('code', 'コメントで宣言', 'Declare in comments'), ('tune', '値を調整', 'Adjust values'), ('play', '描画で確認', 'Inspect drawing')],
                'files-assets': [('folder_code', 'ファイル・素材', 'Files & assets'), ('settings', '読み込み設定', 'Loading settings'), ('play', 'プレビュー', 'Preview')],
                'drawing': [('settings', '実行環境', 'Runtime'), ('code', '描画・入力コード', 'Drawing & input'), ('play', '結果を確認', 'Inspect output')],
                'device': [('code', 'コード・ライブラリ', 'Code & libraries'), ('camera', '権限・対応確認', 'Permissions & support'), ('play', '端末で試す', 'Try on device')],
                'storage-safety': [('code', '編集中の内容', 'Working state'), ('snapshot', '保存・スナップショット', 'Save / snapshot'), ('restore', '確認・復元', 'Inspect / restore')],
                'export-media': [('record', '出力形式を選ぶ', 'Choose format'), ('save', '保存する', 'Save'), ('share_card', '開く・共有', 'Open / share')],
                'settings': [('tag', '整理・設定', 'Organize & configure'), ('save', 'バックアップ', 'Back up'), ('restore', '移行・復元', 'Migrate / restore')]
            }[c['id']]
            for glyph, ja, en in flow:
                out.append(f'<li><svg class="flow-icon" aria-hidden="true" focusable="false"><use href="#app-icon-{glyph}"></use></svg>{bi((ja, en))}</li>')
            out.append('</ol></figure>')
        for t in c['topics']:
            out += [f'<article class="topic" id="{t["id"]}" data-topic>',
                    f'<div class="topic-title"><h3 tabindex="-1">{bi(t["title"])}</h3><a class="permalink" href="#{t["id"]}" {aria("この項目へのリンク", "Link to this topic")}>#</a></div>',
                    f'<p class="topic-lead">{bi(t["lead"])}</p>',
                    f'<div class="access-path"><span class="field-label">{bi(["開く場所", "WHERE"])}</span><p>{bi(t["path"])}</p></div>',
                    f'<h4 class="step-label">{bi(["操作手順", "STEPS"])}</h4><ol class="steps">']
            for step in t['steps']:
                out.append(f'<li>{bi(step)}</li>')
            out += ['</ol>', f'<p class="result"><span class="field-label">{bi(["操作後", "RESULT"])}</span>{bi(t["result"])}</p>',
                    f'<aside class="note"><span class="field-label">{bi(["条件・確認すること", "CONDITIONS & CHECKS"])}</span><p>{bi(t["note"])}</p></aside>']
            table = t.get('table')
            if table:
                out.append(f'<div class="table-scroll" tabindex="0" role="region" {aria(t["title"][0], t["title"][1])}><table><thead><tr>')
                out += [f'<th scope="col">{bi(h)}</th>' for h in table['headers']]
                out.append('</tr></thead><tbody>')
                for row in table['rows']:
                    out.append('<tr>' + ''.join(f'<td>{bi(v)}</td>' for v in row) + '</tr>')
                out.append('</tbody></table></div>')
            for i, example in enumerate(t.get('examples', [])):
                live = example['runtime'] != 'manual'
                attribute = f' data-example="{esc(example["runtime"])}"' if live else ''
                out += [f'<div class="code-example"{attribute}>',
                        f'<div class="code-caption"><span>{bi(example["label"])}</span><button type="button" class="copy enhanced">{bi(["コピー", "Copy"])}</button></div>',
                        f'<pre tabindex="0"><code>{esc(example["code"])}</code></pre></div>']
            if t.get('live'):
                kind = t['live']
                out += [f'<section class="live-demo enhanced" data-live="{kind}" {aria("実行デモ", "Live example")}>',
                        f'<div class="demo-heading"><span class="demo-tag live-tag">{bi(["コードを実行", "LIVE CODE"])}</span><span>{bi(["上のコード例をブラウザーで描画", "Runs the example above in your browser"])}</span></div>',
                        f'<p class="demo-help">{bi(["実行で開始します。停止・初期化できます。パラメーターとウォッチは、この例専用の補助処理でアプリの連携を再現します。", "Press Run to start; stop or reset at any time. Example-specific helpers reproduce parameter and watch integration."])}</p>',
                        '<div class="live-controls"></div><div class="live-stage">',
                        f'<p class="stage-placeholder">{bi(["実行すると、ここに描画します。", "Run the example to draw here."])}</p></div>',
                        f'<div class="demo-actions">{button("start","実行","Run","primary")}{button("stop","停止","Stop")}{button("reset","初期化","Reset")}</div>',
                        f'<p class="demo-status" role="status">{bi(["未実行", "Not started"])}</p></section>']
            elif t.get('demo'):
                out.append(f'<section class="simulation enhanced" data-sim="{esc(t["demo"])}" {aria("模擬操作", "Simulation")}></section>')
            if t.get('links'):
                out.append(f'<nav class="help-links" {aria("関連する説明", "Related instructions")}>'+''.join(f'<a href="#{k}">{bi(titles[k])} →</a>' for k in t['links'])+'</nav>')
            out.append('</article>')
        out.append(f'<nav class="chapter-pager" {aria("章の移動", "Chapter navigation")}>')
        for neighbor, label in [(n - 2, ['前の章', 'Previous']), (n, ['次の章', 'Next'])]:
            if 0 <= neighbor < len(chapters):
                target = chapters[neighbor]
                out.append(f'<a href="#{target["id"]}"><small>{bi(label)}</small><span>{bi(target["title"])} →</span></a>')
        out.append('</nav></section>')
    out += [f'<footer><p>{bi(["このページのデモはブラウザーでの学習用です。Android実機の権限・センサー・コーデックの動作は、使用する端末で確認してください。", "Demos are for learning in a browser. Check permissions, sensors, and codecs on the Android device you use."])}</p>',
            f'<a href="assets/guide/NOTICE.html">{bi(["デモのライブラリとライセンス", "Demo libraries and licenses"])}</a> · <a href="index.html">Edit:RiN</a></footer>',
            f'</main><aside class="page-toc enhanced"><p class="nav-label">'+bi(['この章の項目','ON THIS PAGE'])+f'</p><nav id="page-toc" {aria("章内目次", "In this chapter")}></nav><a class="to-top" href="#content">↑ '+bi(['本文の先頭','Back to content'])+'</a></aside></div>',
            '<p class="sr-only" id="announcement" role="status" aria-live="polite"></p></body></html>']
    return '\n'.join(out) + '\n'

def coverage(data):
    lines = ['# Web manual implementation coverage', '', 'The manual describes user-facing Android functionality. Browser simulations do not verify device behavior.', '',
             'Source paths below are relative to `android/app/src/main/java/com/hikariatelier/app/`; `@` marks a repository-relative path.', '',
             '| Chapter / anchor | Implementation inspected |', '| --- | --- |']
    for c in data['chapters']:
        for t in c['topics']:
            lines.append(f'| {c["title"][1]} / `#{t["id"]}` | '+', '.join(f'`{s}`' for s in t['sources'])+' |')
    lines += ['', '## Review rules', '',
              '- Review menu labels and access paths alongside the source behavior when updating the app.',
              '- Update `scripts/guide-content.json` and regenerate with `python3 scripts/build-guide.py`.',
              '- Run `python3 scripts/build-guide.py --check` and the guide browser test after changes.',
              '- Keep source examples executable, preserve existing anchors, and keep Japanese/English steps equivalent.',
              '- Preserve conditions for reruns, single-HTML dependencies, canvas-only capture, device permissions, and external synchronization.',
              '- Android minimum: `android/variables.gradle`; runtime versions: `EditorSessionViewModel.kt`; limits: `ProjectAssets.kt`, `ProjectPaths.kt`, `SketchDownloadAdmission.kt`.', '']
    return '\n'.join(lines)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    data = json.loads(CONTENT.read_text())
    outputs = {ROOT / 'website/guide.html': render(data), ROOT / 'docs/GUIDE_COVERAGE.md': coverage(data)}
    for path, content in outputs.items():
        if args.check:
            if not path.is_file() or path.read_text() != content:
                raise SystemExit(f'Out of date: {path.relative_to(ROOT)}; run python3 scripts/build-guide.py')
        else:
            path.write_text(content)
    print('Guide HTML and coverage are current.' if args.check else 'Generated guide HTML and coverage.')

if __name__ == '__main__':
    main()
