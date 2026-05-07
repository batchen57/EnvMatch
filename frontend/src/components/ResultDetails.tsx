import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import ReactECharts from 'echarts-for-react';
import { Download, Play, ArrowLeft } from 'lucide-react';

export function ResultDetails() {
  const navigate = useNavigate();
  const { id } = useParams();
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!id) return;
    fetch(`http://localhost:8000/tasks/${id}`)
      .then(r => r.json())
      .then(d => {
        setData(d);
        setLoading(false);
      })
      .catch(e => {
        console.error(e);
        setLoading(false);
      });
  }, [id]);

  if (loading) return <div className="p-8 text-center text-muted-foreground">加载中...</div>;
  if (!data || !data.result) return <div className="p-8 text-center text-red-500">获取报告失败或任务未完成</div>;

  const { task, result } = data;
  const dims = result.dimension_scores || {};
  
  const radarOption = {
    radar: {
      indicator: [
        { name: '建筑结构', max: 100 },
        { name: '植被绿化', max: 100 },
        { name: '光照天气', max: 100 },
        { name: '设施物体', max: 100 },
        { name: '道路地面', max: 100 }
      ],
      axisName: { color: '#94a3b8' },
      splitArea: { show: false },
      splitLine: { lineStyle: { color: ['#1e293b'] } },
      axisLine: { lineStyle: { color: '#1e293b' } },
      center: ['50%', '50%'],
      radius: '65%'
    },
    series: [
      {
        type: 'radar',
        data: [
          { value: [
              dims.architecture || 0, 
              dims.vegetation || 0, 
              dims.lighting_weather || 0, 
              dims.facilities || 0, 
              dims.road_surface || 0
            ], 
            name: '环境评分', areaStyle: { color: 'rgba(59, 130, 246, 0.4)' }, lineStyle: { color: '#3b82f6' }, itemStyle: { color: '#3b82f6' } }
        ]
      }
    ]
  };

  const ringOption = {
    series: [
      {
        type: 'pie',
        radius: ['75%', '90%'],
        avoidLabelOverlap: false,
        label: { show: false },
        data: [
          { value: task.similarity_score || 0, itemStyle: { color: '#ef4444' } },
          { value: 100 - (task.similarity_score || 0), itemStyle: { color: '#1e293b' } }
        ]
      }
    ]
  };

  return (
    <div className="bg-card rounded-xl p-6 border border-border h-full flex flex-col">
      <div className="flex justify-between items-center mb-6 border-b border-border pb-4 shrink-0">
        <h3 className="text-lg font-medium flex items-center gap-3">
          <button onClick={() => navigate('/tasks')} className="p-1.5 hover:bg-muted rounded-md transition-colors text-muted-foreground hover:text-foreground cursor-pointer">
            <ArrowLeft className="w-5 h-5" />
          </button>
          结果详情 <span className="text-sm text-muted-foreground font-normal">/ {id}</span>
        </h3>
        <button className="bg-primary/20 text-primary hover:bg-primary/30 px-3 py-1.5 rounded-md text-sm flex items-center gap-2 cursor-pointer transition-colors">
          <Download className="w-4 h-4" /> 导出报告
        </button>
      </div>

      <div className="flex gap-6 flex-1 min-h-0 overflow-y-auto custom-scrollbar">
        {/* Left column */}
        <div className="w-[30%] flex flex-col items-center">
          <h4 className="text-sm font-medium w-full mb-4">相似度评分</h4>
          <div className="relative w-40 h-40 shrink-0">
            <ReactECharts option={ringOption} style={{ height: '100%', width: '100%' }} />
            <div className="absolute inset-0 flex flex-col items-center justify-center">
              <span className="text-4xl font-bold text-foreground">{task.similarity_score?.toFixed(1) || 0}<span className="text-xl">%</span></span>
              <span className="text-xs text-red-500 mt-1">综合结果</span>
            </div>
          </div>
          
          <h4 className="text-sm font-medium w-full mt-8 mb-2">评分分布</h4>
          <div className="w-full h-[200px] shrink-0">
             <ReactECharts option={radarOption} style={{ height: '100%' }} />
          </div>
        </div>

        {/* Middle column: Video & Keyframes */}
        <div className="flex-1 min-w-0">
           <h4 className="text-sm font-medium mb-4">视频对比（关键帧对齐）</h4>
           <div className="flex gap-4">
             <div className="flex-1">
               <div className="text-xs text-muted-foreground mb-2">视频 A</div>
               <div className="bg-black aspect-video rounded-lg overflow-hidden">
                 <video
                   className="w-full h-full object-contain"
                   controls
                   preload="metadata"
                   src={`http://localhost:8000/storage/${(task.video_a_path || '').split('\\').pop()?.split('/').pop()}`}
                 >
                   您的浏览器不支持视频播放
                 </video>
               </div>
             </div>
             <div className="flex-1">
               <div className="text-xs text-muted-foreground mb-2">视频 B</div>
               <div className="bg-black aspect-video rounded-lg overflow-hidden">
                 <video
                   className="w-full h-full object-contain"
                   controls
                   preload="metadata"
                   src={`http://localhost:8000/storage/${(task.video_b_path || '').split('\\').pop()?.split('/').pop()}`}
                 >
                   您的浏览器不支持视频播放
                 </video>
               </div>
             </div>
           </div>

           <h4 className="text-sm font-medium mt-6 mb-2">环境要素抽取 (Keyframes)</h4>
           <div className="flex gap-2 overflow-x-auto pb-2 custom-scrollbar">
             {result.key_frames_a && result.key_frames_a.length > 0 ? result.key_frames_a.map((kf: string, i: number) => (
               <div key={`a-${i}`} className="w-24 shrink-0 aspect-video bg-muted rounded flex items-center justify-center text-[10px] text-muted-foreground overflow-hidden border border-transparent">
                 Frame A{i}
               </div>
             )) : <span className="text-xs text-muted-foreground">无关键帧数据 (需FFmpeg支持)</span>}
           </div>

           <div className="mt-6 border-t border-border pt-4">
             <div className="flex gap-6 mb-4 text-sm">
               <div className="text-primary font-medium cursor-pointer border-b border-primary pb-1">AI 综合描述</div>
             </div>
             <p className="text-sm text-foreground leading-relaxed bg-muted/20 p-4 rounded-lg border border-border/50">
               {result.summary || '无描述信息'}
             </p>
             <div className="grid grid-cols-2 gap-4 mt-4">
               <div className="bg-emerald-500/10 border border-emerald-500/20 p-3 rounded-lg">
                 <h5 className="text-xs font-bold text-emerald-500 mb-2">相似点</h5>
                 <ul className="text-xs text-foreground space-y-1 list-disc list-inside">
                   {result.similar_points?.map((p: string, i: number) => <li key={i}>{p}</li>)}
                 </ul>
               </div>
               <div className="bg-amber-500/10 border border-amber-500/20 p-3 rounded-lg">
                 <h5 className="text-xs font-bold text-amber-500 mb-2">差异点</h5>
                 <ul className="text-xs text-foreground space-y-1 list-disc list-inside">
                   {result.difference_points?.map((p: string, i: number) => <li key={i}>{p}</li>)}
                 </ul>
               </div>
             </div>
           </div>
        </div>

        {/* Right column: Basics */}
        <div className="w-[25%] flex flex-col pl-4 border-l border-border/50 shrink-0">
          <h4 className="text-sm font-medium mb-4">基本信息</h4>
          <div className="space-y-3 text-xs bg-muted/20 p-4 rounded-lg">
            <div className="flex flex-col gap-1"><span className="text-muted-foreground">任务名称:</span> <span className="font-medium text-base">{task.task_name}</span></div>
            <div className="flex flex-col gap-1"><span className="text-muted-foreground">创建时间:</span> <span className="text-sm">{new Date(task.created_at).toLocaleString()}</span></div>
            <div className="flex flex-col gap-1"><span className="text-muted-foreground">完成时间:</span> <span className="text-sm">{task.updated_at ? new Date(task.updated_at).toLocaleString() : '--'}</span></div>
            <div className="flex flex-col gap-1"><span className="text-muted-foreground">耗时:</span> <span className="text-sm font-medium">{task.updated_at && task.created_at ? `${Math.round((new Date(task.updated_at).getTime() - new Date(task.created_at).getTime()) / 1000)} 秒` : '--'}</span></div>
            <div className="flex flex-col gap-1"><span className="text-muted-foreground">模型版本:</span> <span className="text-sm">{task.model_id || 'Gemini 1.5 Pro'}</span></div>
            <div className="flex flex-col gap-1 border-t border-border/50 pt-2 mt-2">
              <div className="flex justify-between items-center"><span className="text-muted-foreground">输入 Token:</span> <span className="text-sm font-medium">{task.input_tokens || 0}</span></div>
              <div className="flex justify-between items-center"><span className="text-muted-foreground">输出 Token:</span> <span className="text-sm font-medium">{task.output_tokens || 0}</span></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
