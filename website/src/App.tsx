const repo = 'https://github.com/jiacimu-droid/lulu'
const releases = `${repo}/releases`

const features = [
  ['🧠', '连续记忆', '长期记忆与原始时间线分层，重要经历可回查来源。'],
  ['💬', '角色与群聊', '从空白开始创建角色，人设、世界书和关系边界贯穿所有出口。'],
  ['☎️', '电话与主动联系', '角色可以打电话、发送主动消息，并把真实行动写入共同经历。'],
  ['📖', '共读与小剧场', '上传文档与角色共同阅读，也可以创建和续写自己的小剧场。'],
  ['⏱️', '学习与待办', '简洁番茄钟、手动今日待办，以及角色可读取的真实学习记录。'],
  ['🎲', '共同游戏', '五子棋与快艇骰子支持角色参与、结果留档和后续提及。'],
]

export default function App() {
  return (
    <main className="min-h-screen bg-[#f7f7f2] text-slate-900">
      <nav className="sticky top-0 z-20 border-b border-slate-200/80 bg-[#f7f7f2]/90 backdrop-blur-xl">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-6">
          <a href="#top" className="flex items-center gap-3 font-semibold">
            <img src="/icon.png" alt="Lulu" className="h-9 w-9 rounded-xl" />
            <span>Lulu</span>
          </a>
          <div className="flex items-center gap-5 text-sm text-slate-600">
            <a href="#features" className="hidden hover:text-slate-950 sm:block">功能</a>
            <a href="#privacy" className="hidden hover:text-slate-950 sm:block">隐私</a>
            <a href={repo} target="_blank" rel="noreferrer" className="hover:text-slate-950">源代码</a>
          </div>
        </div>
      </nav>

      <section id="top" className="relative overflow-hidden px-6 pb-24 pt-20 md:pb-32 md:pt-28">
        <div className="absolute left-1/2 top-12 h-80 w-80 -translate-x-1/2 rounded-full bg-[#c8d8c0]/45 blur-3xl" />
        <div className="relative mx-auto max-w-5xl text-center">
          <img src="/icon.png" alt="Lulu 应用图标" className="mx-auto mb-8 h-28 w-28 rounded-[30px] shadow-xl shadow-slate-300/50" />
          <p className="mb-5 text-sm font-medium tracking-[0.28em] text-[#637b67]">OPEN-SOURCE ANDROID AI COMPANION</p>
          <h1 className="text-5xl font-bold tracking-tight md:text-7xl">让角色真正生活在手机里</h1>
          <p className="mx-auto mt-7 max-w-3xl text-lg leading-8 text-slate-600 md:text-xl">
            Lulu 把角色卡、长期记忆、电话、主动消息、共读、学习和共同游戏连接成同一段连续关系。
            没有默认角色，也不替用户预设性格或亲密程度。
          </p>
          <div className="mt-10 flex flex-col justify-center gap-4 sm:flex-row">
            <a href={releases} target="_blank" rel="noreferrer" className="rounded-full bg-slate-900 px-8 py-4 font-semibold text-white transition hover:-translate-y-0.5 hover:bg-slate-700">
              下载最新 APK
            </a>
            <a href={repo} target="_blank" rel="noreferrer" className="rounded-full border border-slate-300 bg-white/70 px-8 py-4 font-semibold transition hover:-translate-y-0.5 hover:border-slate-500">
              查看 GitHub
            </a>
          </div>
          <p className="mt-5 text-sm text-slate-500">免费开源 · GNU AGPL v3 · 模型服务由用户自行配置</p>
        </div>
      </section>

      <section id="features" className="bg-white px-6 py-24">
        <div className="mx-auto max-w-6xl">
          <p className="text-sm font-semibold tracking-[0.2em] text-[#637b67]">ONE CONTINUOUS LIFE</p>
          <h2 className="mt-3 text-3xl font-bold md:text-5xl">不是一堆互不相干的功能</h2>
          <p className="mt-5 max-w-2xl text-lg leading-8 text-slate-600">聊天、电话、群聊和数字活动共享角色人设、世界书、记忆与关系上下文，发生过的事会留下真实记录。</p>
          <div className="mt-12 grid gap-5 md:grid-cols-2 lg:grid-cols-3">
            {features.map(([icon, title, text]) => (
              <article key={title} className="rounded-3xl border border-slate-200 bg-[#fbfbf7] p-7">
                <div className="text-3xl">{icon}</div>
                <h3 className="mt-5 text-xl font-semibold">{title}</h3>
                <p className="mt-3 leading-7 text-slate-600">{text}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section id="privacy" className="px-6 py-24">
        <div className="mx-auto grid max-w-6xl gap-12 md:grid-cols-[1fr_1.1fr] md:items-center">
          <div>
            <p className="text-sm font-semibold tracking-[0.2em] text-[#637b67]">LOCAL FIRST</p>
            <h2 className="mt-3 text-3xl font-bold md:text-5xl">角色属于用户，不属于服务器</h2>
          </div>
          <div className="rounded-3xl bg-slate-900 p-8 text-slate-100 md:p-10">
            <ul className="space-y-5 leading-7 text-slate-300">
              <li>角色、聊天、记忆与设置默认保存在本机。</li>
              <li>公开构建不包含项目方分析、崩溃上报、远程配置或共享 API Key。</li>
              <li>模型、语音、搜索和云同步仅在用户主动配置并使用时连接对应第三方。</li>
              <li>敏感权限按功能申请，可随时撤销；应用内提供完整数据清除入口。</li>
            </ul>
            <a href={`${repo}/blob/master/docs/PRIVACY.md`} target="_blank" rel="noreferrer" className="mt-8 inline-block font-semibold text-white underline decoration-[#9fb49f] underline-offset-4">
              阅读完整隐私说明
            </a>
          </div>
        </div>
      </section>

      <footer className="border-t border-slate-200 px-6 py-10 text-sm text-slate-500">
        <div className="mx-auto flex max-w-6xl flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <p>Lulu · 独立社区项目，基于 RikkaHub 修改</p>
          <div className="flex gap-5">
            <a href={repo} target="_blank" rel="noreferrer" className="hover:text-slate-900">GitHub</a>
            <a href={`${repo}/blob/master/LICENSE`} target="_blank" rel="noreferrer" className="hover:text-slate-900">AGPL v3</a>
            <a href={`${repo}/blob/master/NOTICE`} target="_blank" rel="noreferrer" className="hover:text-slate-900">上游归属</a>
          </div>
        </div>
      </footer>
    </main>
  )
}
