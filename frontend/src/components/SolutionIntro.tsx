import { Video, Image, Zap, Cpu, ArrowRight, CheckCircle2, ShieldCheck, Layers, Gauge } from 'lucide-react';
import { motion } from 'framer-motion';

export function SolutionIntro() {
  return (
    <div className="max-w-6xl mx-auto space-y-12 pb-20 animate-in fade-in duration-700">
      {/* Header Section */}
      <div className="text-center space-y-4">
        <motion.div 
          initial={{ scale: 0.9, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          className="inline-block p-2 bg-primary/10 rounded-2xl mb-2"
        >
          <ShieldCheck className="w-8 h-8 text-primary" />
        </motion.div>
        <h2 className="text-3xl font-extrabold tracking-tight sm:text-4xl bg-clip-text text-transparent bg-gradient-to-r from-foreground to-foreground/70">
          EnvMatch 技术方案全景图
        </h2>
        <p className="max-w-2xl mx-auto text-muted-foreground text-lg">
          深度解析平台如何通过多模态大模型与智能预处理引擎，实现像素级环境相似度评测。
        </p>
      </div>

      {/* Main Solutions Comparison */}
      <div className="grid md:grid-cols-2 gap-8">
        {/* Solution A: Native Video */}
        <motion.div 
          whileHover={{ y: -5 }}
          className="relative group p-8 bg-card border border-border rounded-3xl shadow-xl overflow-hidden"
        >
          <div className="absolute top-0 right-0 p-6 opacity-10 group-hover:opacity-20 transition-opacity">
            <Video className="w-32 h-32" />
          </div>
          <div className="relative z-10">
            <div className="flex items-center gap-3 mb-6">
              <div className="w-12 h-12 rounded-xl bg-blue-500/20 flex items-center justify-center">
                <Video className="w-6 h-6 text-blue-500" />
              </div>
              <div>
                <h3 className="text-xl font-bold">原生视频流识别</h3>
                <span className="text-[10px] uppercase tracking-widest text-blue-500 font-bold">Native Video Stream</span>
              </div>
            </div>
            
            <p className="text-sm text-muted-foreground leading-relaxed mb-8">
              直接将完整的原始视频流投喂给支持超长上下文（Long Context）的多模态模型。AI 像人类一样通过“观看”整个过程来获取环境的动态变化信息。
            </p>

            <div className="space-y-4 mb-8">
              <FeatureItem icon={CheckCircle2} text="时序特征全感知：捕捉光影闪烁、云层移动等动态差异" />
              <FeatureItem icon={CheckCircle2} text="深度空间理解：跨越镜头平移的环境逻辑一致性校验" />
              <FeatureItem icon={CheckCircle2} text="零信息损耗：无需任何预采样，保留每一帧原始信息" />
            </div>

            {/* Visualization Metaphor */}
            <div className="p-4 bg-muted/30 rounded-2xl border border-border/50">
              <div className="text-xs font-bold text-primary mb-3 flex items-center gap-2">
                <Gauge className="w-3 h-3" /> 方案隐喻：全景侦察
              </div>
              <div className="flex items-center justify-center h-24 gap-1">
                {[1, 2, 3, 4, 5, 6, 7].map(i => (
                  <motion.div 
                    key={i}
                    animate={{ height: [20, 50, 30, 60, 20][i % 5] }}
                    transition={{ repeat: Infinity, duration: 2, delay: i * 0.2 }}
                    className="w-2 bg-blue-500/40 rounded-full"
                  />
                ))}
                <div className="mx-4 text-xs text-muted-foreground">连续流处理</div>
                {[1, 2, 3, 4, 5, 6, 7].map(i => (
                  <motion.div 
                    key={i}
                    animate={{ height: [60, 20, 50, 30, 40][i % 5] }}
                    transition={{ repeat: Infinity, duration: 2, delay: i * 0.1 }}
                    className="w-2 bg-blue-500/40 rounded-full"
                  />
                ))}
              </div>
            </div>
          </div>
        </motion.div>

        {/* Solution B: Stitched Matrix */}
        <motion.div 
          whileHover={{ y: -5 }}
          className="relative group p-8 bg-card border border-border rounded-3xl shadow-xl overflow-hidden"
        >
          <div className="absolute top-0 right-0 p-6 opacity-10 group-hover:opacity-20 transition-opacity">
            <Layers className="w-32 h-32" />
          </div>
          <div className="relative z-10">
            <div className="flex items-center gap-3 mb-6">
              <div className="w-12 h-12 rounded-xl bg-emerald-500/20 flex items-center justify-center">
                <Layers className="w-6 h-6 text-emerald-500" />
              </div>
              <div>
                <h3 className="text-xl font-bold">时序网格缝合识别</h3>
                <span className="text-[10px] uppercase tracking-widest text-emerald-500 font-bold">Stitched Matrix Inference</span>
              </div>
            </div>
            
            <p className="text-sm text-muted-foreground leading-relaxed mb-8">
              通过智能采样获取关键帧，并将其“无缝缝合”成一张超高分辨率的对比图。利用模型的空间关联分析能力，在同一个视觉上下文中进行像素级比对。
            </p>

            <div className="space-y-4 mb-8">
              <FeatureItem icon={CheckCircle2} text="极致性价比：Token 消耗仅为视频流模式的 1/50" />
              <FeatureItem icon={CheckCircle2} text="空间对比增强：强迫 AI 在同一视野下寻找 A/B 差异" />
              <FeatureItem icon={CheckCircle2} text="快速响应：单图推理速度极快，适用于高并发场景" />
            </div>

            {/* Visualization Metaphor */}
            <div className="p-4 bg-muted/30 rounded-2xl border border-border/50">
              <div className="text-xs font-bold text-primary mb-3 flex items-center gap-2">
                <Gauge className="w-3 h-3" /> 方案隐喻：手术显微镜
              </div>
              <div className="grid grid-cols-4 gap-1 h-24">
                {[1, 2, 3, 4, 5, 6, 7, 8].map(i => (
                  <div key={i} className="bg-emerald-500/20 border border-emerald-500/30 rounded flex items-center justify-center overflow-hidden relative">
                    <Image className="w-4 h-4 text-emerald-500/40" />
                    <motion.div 
                      animate={{ opacity: [0, 1, 0] }}
                      transition={{ repeat: Infinity, duration: 1.5, delay: i * 0.3 }}
                      className="absolute inset-0 bg-emerald-500/10"
                    />
                  </div>
                ))}
              </div>
            </div>
          </div>
        </motion.div>
      </div>

      {/* Sampling Strategy Section */}
      <div className="bg-muted/30 border border-border rounded-3xl p-10">
        <div className="flex flex-col md:flex-row gap-10 items-center">
          <div className="flex-1 space-y-6">
            <h3 className="text-2xl font-bold flex items-center gap-3">
              <Zap className="w-6 h-6 text-yellow-500" />
              智能采样策略对比
            </h3>
            <p className="text-muted-foreground">
              采样策略决定了“网格缝合方案”中哪些帧有资格进入最终的 AI 推理上下文。
            </p>

            <div className="space-y-6">
              <div className="p-5 bg-card rounded-2xl border border-border shadow-sm">
                <div className="flex items-center gap-3 mb-2">
                  <div className="w-8 h-8 rounded-lg bg-slate-500/10 flex items-center justify-center">
                    <Gauge className="w-4 h-4 text-slate-500" />
                  </div>
                  <h4 className="font-bold">按秒抽帧 (Fixed FPS)</h4>
                </div>
                <p className="text-xs text-muted-foreground">
                  像节拍器一样，严格按照固定频率采样。逻辑简单稳健，适合环境静止、光影均匀的视频场景。
                </p>
              </div>

              <div className="p-5 bg-card rounded-2xl border border-primary/30 shadow-sm relative overflow-hidden">
                <div className="absolute top-0 right-0 bg-primary text-primary-foreground text-[10px] px-3 py-1 rounded-bl-xl font-bold">推荐方案</div>
                <div className="flex items-center gap-3 mb-2">
                  <div className="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center">
                    <Zap className="w-4 h-4 text-primary" />
                  </div>
                  <h4 className="font-bold">感知抽帧 (Perceptual Sampling)</h4>
                </div>
                <p className="text-xs text-muted-foreground mb-3">
                  集成了 **PySceneDetect (场景检测)** 与 **Optical Flow (光流法)** 的高级算法。
                </p>
                <ul className="text-[10px] space-y-1.5 text-muted-foreground italic">
                  <li className="flex items-center gap-2"><ArrowRight className="w-3 h-3 text-primary" /> 锁定镜头切换：只在画面内容发生剧变时采样</li>
                  <li className="flex items-center gap-2"><ArrowRight className="w-3 h-3 text-primary" /> 运动距离感应：摄像机平移距离超过阈值时强制补帧</li>
                </ul>
              </div>
            </div>
          </div>

          <div className="flex-1 w-full flex items-center justify-center">
             <div className="relative w-full max-w-sm aspect-square bg-card border border-border rounded-full flex items-center justify-center p-8">
                {/* Visual Diagram Placeholder - CSS Animation */}
                <div className="absolute inset-0 border-4 border-dashed border-border/40 rounded-full animate-[spin_20s_linear_infinite]" />
                <div className="relative z-10 flex flex-col items-center gap-4">
                  <div className="w-20 h-20 bg-primary rounded-full flex items-center justify-center shadow-lg shadow-primary/30">
                    <Cpu className="w-10 h-10 text-primary-foreground" />
                  </div>
                  <div className="text-center">
                    <div className="text-lg font-bold">核心预处理引擎</div>
                    <div className="text-xs text-muted-foreground">处理每秒 60 帧的原始负载</div>
                  </div>
                </div>
                {/* Floating Icons */}
                <motion.div animate={{ y: [0, -10, 0] }} transition={{ repeat: Infinity, duration: 3 }} className="absolute top-10 left-10 p-3 bg-card border border-border rounded-xl shadow-lg">
                  <Video className="w-5 h-5 text-blue-500" />
                </motion.div>
                <motion.div animate={{ y: [0, 10, 0] }} transition={{ repeat: Infinity, duration: 4, delay: 1 }} className="absolute bottom-10 right-10 p-3 bg-card border border-border rounded-xl shadow-lg">
                  <Image className="w-5 h-5 text-emerald-500" />
                </motion.div>
                <motion.div animate={{ x: [0, 10, 0] }} transition={{ repeat: Infinity, duration: 5, delay: 0.5 }} className="absolute top-1/2 right-0 translate-x-1/2 p-3 bg-card border border-border rounded-xl shadow-lg">
                  <Zap className="w-5 h-5 text-yellow-500" />
                </motion.div>
             </div>
          </div>
        </div>
      </div>

      {/* Final Callout */}
      <div className="text-center p-10 border border-dashed border-border rounded-3xl bg-muted/10">
        <h4 className="text-lg font-bold mb-2">如何选择？</h4>
        <p className="text-sm text-muted-foreground mb-6 max-w-xl mx-auto">
          通常建议优先使用 <b>感知抽帧 + 图像识别</b> 方案，它在保持极高精度的同时，能为您节省约 90% 的算力成本。
          只有在处理包含极短关键动作、或需要模型理解长线逻辑的复杂环境时，才推荐切换至 <b>原生视频识别</b>。
        </p>
      </div>
    </div>
  );
}

function FeatureItem({ icon: Icon, text }: { icon: any, text: string }) {
  return (
    <div className="flex items-center gap-2 text-xs font-medium">
      <Icon className="w-4 h-4 text-emerald-500 shrink-0" />
      <span>{text}</span>
    </div>
  );
}
