import React from 'react';
import { Play, Settings2, Cpu, FileJson, MonitorSmartphone } from 'lucide-react';
import { cn } from '@/lib/utils';

export function Pipeline() {
  const steps = [
    { num: '01', title: '输入层', sub: '视频上传', icon: Play, desc: '支持多格式', color: 'bg-[#3b82f6]/20 text-[#3b82f6] ring-[#3b82f6]/40', line: 'bg-[#3b82f6]/50', arrow: 'border-[#3b82f6]/50' },
    { num: '02', title: '处理层', sub: '抽帧/增强/去噪', icon: Settings2, desc: '构建环境特征', color: 'bg-[#10b981]/20 text-[#10b981] ring-[#10b981]/40', line: 'bg-[#10b981]/50', arrow: 'border-[#10b981]/50' },
    { num: '03', title: '模型层', sub: '多模态大模型', icon: Cpu, desc: '计算相似度', color: 'bg-[#8b5cf6]/20 text-[#8b5cf6] ring-[#8b5cf6]/40', line: 'bg-[#8b5cf6]/50', arrow: 'border-[#8b5cf6]/50' },
    { num: '04', title: '输出层', sub: '相似度评分', icon: FileJson, desc: '报告生成', color: 'bg-[#f59e0b]/20 text-[#f59e0b] ring-[#f59e0b]/40', line: 'bg-[#f59e0b]/50', arrow: 'border-[#f59e0b]/50' },
    { num: '05', title: '应用层', sub: 'API 集成', icon: MonitorSmartphone, desc: '业务应用', color: 'bg-[#06b6d4]/20 text-[#06b6d4] ring-[#06b6d4]/40', line: 'bg-[#06b6d4]/50', arrow: 'border-[#06b6d4]/50' },
  ];

  return (
    <div className="bg-card rounded-xl p-6 border border-border flex flex-col justify-center relative shadow-sm h-[260px]">
      <h3 className="text-lg font-medium absolute top-6 left-6">分析流程</h3>
      
      <div className="flex justify-between items-center w-full px-2 mt-8">
        {steps.map((step, idx) => (
          <div key={idx} className="flex flex-col items-center flex-1 relative group">
            {/* Top Text */}
            <div className="text-center flex flex-col gap-1 mb-5 h-10 justify-end">
              <div className="text-[12px] text-muted-foreground font-mono tracking-wider">Step {step.num}</div>
              <div className="text-sm font-semibold tracking-wider text-foreground">{step.title}</div>
            </div>
            
            {/* Middle Circle and Exact Line */}
            <div className="relative flex items-center justify-center w-full py-2">
              {/* Connecting Line (exact width between circles) */}
              {idx < steps.length - 1 && (
                <div className="absolute top-1/2 left-[calc(50%+34px)] w-[calc(100%-68px)] flex items-center justify-center pointer-events-none z-0">
                  <div className={cn("w-full h-[2px] rounded-full relative", step.line)}>
                    {/* Exact Arrow Head at the right end of the line */}
                    <div className={cn("absolute right-0 top-1/2 -translate-y-1/2 w-2 h-2 border-t-2 border-r-2 rotate-45 transform -translate-x-[2px]", step.arrow)}></div>
                  </div>
                </div>
              )}
              
              {/* Glowing Circle */}
              <div className="relative z-10 bg-card rounded-full">
                <div className={cn(
                  "h-12 w-12 rounded-full flex items-center justify-center ring-4 ring-offset-4 ring-offset-card transition-all duration-300 group-hover:scale-110",
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
    </div>
  );
}
