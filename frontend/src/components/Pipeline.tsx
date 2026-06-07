import { useState } from 'react';
import { Play, Settings2, Cpu, FileJson, MonitorSmartphone, X, Info, CheckCircle2, Layers } from 'lucide-react';
import { cn } from '@/lib/utils';
import { motion, AnimatePresence } from 'framer-motion';

export function Pipeline() {
  const [activeStep, setActiveStep] = useState<any>(null);

  const steps = [
    { 
      num: '01', 
      title: '输入层', 
      sub: '视频上传', 
      icon: Play, 
      desc: '支持多格式', 
      color: 'bg-[#3b82f6]/20 text-[#3b82f6] ring-[#3b82f6]/40', 
      line: 'bg-[#3b82f6]/50', 
      arrow: 'border-[#3b82f6]/50',
      details: {
        intro: '分析流程的起始环节，负责多维视频数据的采集与初步验证。',
        features: ['支持 MP4, AVI, MOV 等主流视频格式', '支持大文件切片上传与断点续传', '自动校验视频编码、分辨率与元数据信息', '提供任务优先级队列管理'],
        tech: 'FFmpeg Meta-Probe, Pydantic Validation'
      }
    },
    { 
      num: '02', 
      title: '处理层', 
      sub: '抽帧/增强/去噪', 
      icon: Settings2, 
      desc: '构建环境特征', 
      color: 'bg-[#10b981]/20 text-[#10b981] ring-[#10b981]/40', 
      line: 'bg-[#10b981]/50', 
      arrow: 'border-[#10b981]/50',
      details: {
        intro: '将原始视频转化为 AI 可理解的特征序列，是相似度计算的基础。',
        features: ['基于 PySceneDetect 的智能关键帧抽取', '图像自动增强与光照补偿', '背景/前景分离与运动轨迹过滤', '视频多尺度特征对齐处理'],
        tech: 'OpenCV, Scenedetect, NumPy'
      }
    },
    { 
      num: '03', 
      title: '模型层', 
      sub: '多模态大模型', 
      icon: Cpu, 
      desc: '计算相似度', 
      color: 'bg-[#8b5cf6]/20 text-[#8b5cf6] ring-[#8b5cf6]/40', 
      line: 'bg-[#8b5cf6]/50', 
      arrow: 'border-[#8b5cf6]/50',
      details: {
        intro: '核心大脑，利用最先进的多模态大模型（VLM）进行深层环境理解。',
        features: ['多模型动态路由（Gemini / GPT / MiniMax）', '高精度环境维度打分逻辑', '支持 Zero-shot 环境要素识别', '语义相似度与空间一致性协同分析'],
        tech: 'Gemini 2.5 Pro, GPT-4o, Qwen-VL, MiniMax-M2.7'
      }
    },
    { 
      num: '04', 
      title: '输出层', 
      sub: '相似度评分', 
      icon: FileJson, 
      desc: '报告生成', 
      color: 'bg-[#f59e0b]/20 text-[#f59e0b] ring-[#f59e0b]/40', 
      line: 'bg-[#f59e0b]/50', 
      arrow: 'border-[#f59e0b]/50',
      details: {
        intro: '将复杂的模型计算结果转化为直观、可解释的业务分析报告。',
        features: ['自动生成多维环境相似度 PDF 报告', '结构化 JSON 数据输出与存证', '相似点/差异点可视化图表渲染', '基于置信度的风险预警标注'],
        tech: 'jsPDF, ECharts, Modern-Screenshot'
      }
    },
    { 
      num: '05', 
      title: '应用层', 
      sub: 'API 集成', 
      icon: MonitorSmartphone, 
      desc: '业务应用', 
      color: 'bg-[#06b6d4]/20 text-[#06b6d4] ring-[#06b6d4]/40', 
      line: 'bg-[#06b6d4]/50', 
      arrow: 'border-[#06b6d4]/50',
      details: {
        intro: '实现系统与外部业务逻辑的深度对接与自动化集成。',
        features: ['标准 RESTful API 接口调用', '第三方审核系统实时回调通知', '多终端看板同步与大屏展示', '历史任务追溯与全流程审计'],
        tech: 'FastAPI, PostgreSQL, WebSocket'
      }
    },
  ];

  return (
    <div className="bg-card rounded-xl p-6 border border-border flex flex-col justify-center relative shadow-sm h-[260px]">
      <h3 className="text-lg font-medium absolute top-6 left-6 flex items-center gap-2">
        <Layers className="w-5 h-5 text-primary" />
        分析流程架构
      </h3>
      
      <div className="flex justify-between items-center w-full px-2 mt-8">
        {steps.map((step, idx) => (
          <div 
            key={idx} 
            className="flex flex-col items-center flex-1 relative group cursor-pointer"
            onClick={() => setActiveStep(step)}
          >
            {/* Top Text */}
            <div className="text-center flex flex-col gap-1 mb-5 h-10 justify-end">
              <div className="text-[12px] text-muted-foreground font-mono tracking-wider">Step {step.num}</div>
              <div className="text-sm font-semibold tracking-wider text-foreground group-hover:text-primary transition-colors">{step.title}</div>
            </div>
            
            {/* Middle Circle and Exact Line */}
            <div className="relative flex items-center justify-center w-full py-2">
              {/* Connecting Line */}
              {idx < steps.length - 1 && (
                <div className="absolute top-1/2 left-[calc(50%+34px)] w-[calc(100%-68px)] flex items-center justify-center pointer-events-none z-0">
                  <div className={cn("w-full h-[2px] rounded-full relative", step.line)}>
                    <div className={cn("absolute right-0 top-1/2 -translate-y-1/2 w-2 h-2 border-t-2 border-r-2 rotate-45 transform -translate-x-[2px]", step.arrow)}></div>
                  </div>
                </div>
              )}
              
              {/* Glowing Circle */}
              <div className="relative z-10 bg-card rounded-full p-1">
                <div className={cn(
                  "h-12 w-12 rounded-full flex items-center justify-center ring-4 ring-offset-4 ring-offset-card transition-all duration-500 group-hover:scale-110 group-hover:rotate-[360deg]",
                  step.color
                )}>
                  <step.icon className="h-5 w-5" />
                </div>
              </div>
            </div>
            
            {/* Bottom Text */}
            <div className="text-center flex flex-col gap-1 mt-5 h-10 justify-start">
              <div className="text-sm font-medium text-foreground">{step.sub}</div>
              <div className="text-[12px] text-muted-foreground">{step.desc}</div>
            </div>
          </div>
        ))}
      </div>

      {/* Layer Detail Modal */}
      <AnimatePresence>
        {activeStep && (
          <div className="fixed inset-0 z-[1000] flex items-center justify-center p-4">
            <motion.div 
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="absolute inset-0 bg-black/60 backdrop-blur-sm"
              onClick={() => setActiveStep(null)}
            />
            <motion.div
              initial={{ scale: 0.9, opacity: 0, y: 20 }}
              animate={{ scale: 1, opacity: 1, y: 0 }}
              exit={{ scale: 0.9, opacity: 0, y: 20 }}
              className="relative w-full max-w-lg bg-card border border-border rounded-3xl shadow-2xl overflow-hidden"
            >
              <div className={cn("h-32 flex items-end p-8 relative", activeStep.color.split(' ')[0])}>
                <div className="absolute top-4 right-4">
                  <button onClick={() => setActiveStep(null)} className="p-2 bg-black/20 hover:bg-black/40 rounded-full transition-colors text-white">
                    <X className="w-5 h-5" />
                  </button>
                </div>
                <div className="flex items-center gap-4 text-white">
                  <div className="p-4 bg-white/20 backdrop-blur rounded-2xl">
                    <activeStep.icon className="w-8 h-8" />
                  </div>
                  <div>
                    <div className="text-xs font-mono opacity-80 tracking-widest">LAYER {activeStep.num}</div>
                    <h4 className="text-3xl font-bold">{activeStep.title}</h4>
                  </div>
                </div>
              </div>

              <div className="p-8 space-y-6">
                <div>
                  <h5 className="text-xs font-bold text-muted-foreground uppercase tracking-widest mb-3 flex items-center gap-2">
                    <Info className="w-3.5 h-3.5" /> 层级概述
                  </h5>
                  <p className="text-sm text-foreground leading-relaxed">
                    {activeStep.details.intro}
                  </p>
                </div>

                <div>
                  <h5 className="text-xs font-bold text-muted-foreground uppercase tracking-widest mb-3 flex items-center gap-2">
                    <CheckCircle2 className="w-3.5 h-3.5" /> 核心功能与能力
                  </h5>
                  <ul className="grid grid-cols-1 gap-2">
                    {activeStep.details.features.map((f: string, i: number) => (
                      <li key={i} className="flex items-center gap-3 text-sm text-muted-foreground bg-muted/30 p-2 rounded-lg border border-border/50">
                        <div className={cn("w-1.5 h-1.5 rounded-full shrink-0", activeStep.color.split(' ')[0])} />
                        {f}
                      </li>
                    ))}
                  </ul>
                </div>

                <div className="pt-4 border-t border-border flex justify-between items-center">
                  <div>
                    <div className="text-[10px] text-muted-foreground uppercase font-bold tracking-tighter">关键技术栈</div>
                    <div className="text-xs font-mono mt-1 text-primary">{activeStep.details.tech}</div>
                  </div>
                  <button 
                    onClick={() => setActiveStep(null)}
                    className="px-6 py-2 bg-primary text-primary-foreground rounded-xl text-sm font-bold shadow-lg shadow-primary/20 hover:scale-105 transition-transform"
                  >
                    了解并返回
                  </button>
                </div>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
}
