import React, { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import ReactECharts from 'echarts-for-react';
import { Download, Play, ArrowLeft, Video, Image, Scan, Loader2, X, AlertCircle, Clock, Zap, FileText, Hash, Calendar, Layers, Minus, Plus, RotateCcw } from 'lucide-react';
import { domToPng } from 'modern-screenshot';
import { jsPDF } from 'jspdf';
import { cn } from '@/lib/utils';
import { motion, AnimatePresence } from 'framer-motion';

export function ResultDetails() {
  const navigate = useNavigate();
  const { id } = useParams();
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [selectedImg, setSelectedImg] = useState<string | null>(null);
  const [zoom, setZoom] = useState(100);
  const [pos, setPos] = useState({ x: 0, y: 0 });
  const reportRef = useRef<HTMLDivElement>(null);
  const previewRef = useRef<HTMLDivElement>(null);
  const ringChartRef = useRef<any>(null);
  const radarChartRef = useRef<any>(null);

  const toggleFullScreen = () => {
    if (!previewRef.current) return;
    if (!document.fullscreenElement) {
      previewRef.current.requestFullscreen().catch(err => {
        console.error(`Error attempting to enable full-screen mode: ${err.message}`);
      });
    } else {
      document.exitFullscreen();
    }
  };

  useEffect(() => {
    if (!id) return;
    fetch(`http://localhost:8888/tasks/${id}`)
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

  const waitForImages = (container: HTMLElement): Promise<void> => {
    const images = container.querySelectorAll('img');
    return Promise.all(
      Array.from(images).map(img => {
        return new Promise<void>((resolve) => {
          if (img.complete && img.naturalWidth > 0) resolve();
          else {
            const timeout = setTimeout(resolve, 8000);
            const done = () => { clearTimeout(timeout); resolve(); };
            img.onload = done;
            img.onerror = done;
          }
        });
      })
    );
  };

  const mkImgUrl = (path: string | undefined) => {
    if (!path) return '';
    return `http://localhost:8888/${path.replace(/\\/g, '/').replace(/^storage\//, 'storage/')}`;
  };

  const handleExport = async () => {
    if (!reportRef.current || !data) return;
    setExporting(true);
    const { task, result } = data;
    const score = task.similarity_score ?? 0;
    const scoreColor = score > 70 ? '#10b981' : score > 40 ? '#f59e0b' : '#ef4444';
    const dims = result?.dimension_scores || {};
    const dimKeys: [string, string][] = [
      ['光照/天气', 'lighting_weather'],
      ['建筑风格', 'architecture'],
      ['固定设施', 'facilities'],
      ['植被绿化', 'vegetation'],
      ['地面材质', 'road_surface'],
    ];

    // 1. Capture ECharts as PNG data URLs
    let ringDataUrl = '';
    let radarDataUrl = '';
    try {
      const ri = ringChartRef.current?.getEchartsInstance?.();
      const rdi = radarChartRef.current?.getEchartsInstance?.();
      if (ri) ringDataUrl = ri.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: '#0f172a' });
      if (rdi) radarDataUrl = rdi.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: '#0f172a' });
    } catch (e) { /* best-effort */ }

    // 2. Helpers
    const S = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

    const buildKfRow = (frames: string[] | undefined) =>
      (frames || []).map((kf) => `<img src="${mkImgUrl(kf)}" style="width:180px;height:101px;object-fit:cover;border-radius:6px;border:1px solid rgba(255,255,255,0.1);flex-shrink:0;" />`).join('');

    const buildScoreBars = () =>
      dimKeys.map(([label, key]) => {
        const v = (dims as any)[key] || 0;
        return `<div style="display:flex;align-items:center;gap:14px;margin-bottom:10px;">
          <span style="width:72px;font-size:13px;color:#94a3b8;flex-shrink:0;">${label}</span>
          <div style="flex:1;height:8px;background:rgba(255,255,255,0.06);border-radius:4px;overflow:hidden;">
            <div style="height:100%;width:${v}%;background:${scoreColor};border-radius:4px;"></div>
          </div>
          <span style="width:38px;font-size:13px;color:#e2e8f0;text-align:right;flex-shrink:0;">${v}%</span>
        </div>`;
      }).join('');

    const kfA = buildKfRow(result?.key_frames_a);
    const kfB = buildKfRow(result?.key_frames_b);
    const vidAName = (task.video_a_path || '').split('\\').pop()?.split('/').pop() || '';
    const vidBName = (task.video_b_path || '').split('\\').pop()?.split('/').pop() || '';
    const vidAThumb = result?.key_frames_a?.[0] ? mkImgUrl(result.key_frames_a[0]) : '';
    const vidBThumb = result?.key_frames_b?.[0] ? mkImgUrl(result.key_frames_b[0]) : '';
    const createdAt = new Date(task.created_at).toLocaleString('zh-CN', { hour12: false });
    const updatedAt = task.updated_at ? new Date(task.updated_at).toLocaleString('zh-CN', { hour12: false }) : '--';
    const duration = task.updated_at ? `${Math.round((new Date(task.updated_at).getTime() - new Date(task.created_at).getTime()) / 1000)} 秒` : '--';

    // 3. Build export HTML with section IDs
    const html = `
<style>
  #eroot * { box-sizing:border-box; margin:0; }
  #eroot h3 { font-size:17px; font-weight:600; color:#f1f5f9; margin-bottom:12px; }
  #eroot .card { background:rgba(255,255,255,0.03); border:1px solid rgba(255,255,255,0.06); border-radius:10px; padding:20px 26px; }
  #eroot .lbl { font-size:12px; color:#64748b; }
  #eroot .val { font-size:14px; color:#e2e8f0; margin-top:3px; }
</style>
<div id="eroot" style="width:960px;padding:40px 0 44px;background:#0f172a;color:#e2e8f0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','Microsoft YaHei',sans-serif;line-height:1.55;">

  <div id="sec-header" style="padding:0 50px 20px;margin-bottom:24px;border-bottom:1px solid rgba(255,255,255,0.08);">
    <div style="font-size:28px;font-weight:700;color:#f1f5f9;margin-bottom:4px;">EnvMatch 环境相似度分析报告</div>
    <div style="font-size:13px;color:#64748b;">任务: ${S(task.task_name)} &nbsp;|&nbsp; ID: ${S(task.id || id || '')} &nbsp;|&nbsp; 生成: ${new Date().toLocaleString('zh-CN', { hour12: false })}</div>
  </div>

  <div id="sec-score" style="padding:0 50px;display:flex;gap:36px;align-items:center;margin-bottom:24px;">
    <div style="flex-shrink:0;width:200px;display:flex;flex-direction:column;align-items:center;">
      <div style="position:relative;width:180px;height:180px;">
        ${ringDataUrl ? `<img src="${ringDataUrl}" style="width:180px;height:180px;display:block;" />` : '<div style="width:180px;height:180px;border-radius:50%;border:6px solid rgba(255,255,255,0.08);"></div>'}
        <div style="position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;pointer-events:none;">
          <div style="font-size:36px;font-weight:800;color:${scoreColor};line-height:1;">${task.similarity_score?.toFixed(1) || '--'}<span style="font-size:16px;color:#94a3b8;">${task.similarity_score > 0 ? '%' : ''}</span></div>
          <div style="font-size:12px;color:#64748b;margin-top:2px;">综合相似度</div>
        </div>
      </div>
    </div>
    <div class="card" style="flex:1;display:grid;grid-template-columns:1fr 1fr;gap:14px 28px;">
      <div><div class="lbl">任务名称</div><div class="val" style="font-size:15px;font-weight:600;color:#f1f5f9;">${S(task.task_name)}</div></div>
      <div><div class="lbl">模型标识</div><div class="val" style="font-family:monospace;font-size:13px;">${S(task.model_id || '--')}</div></div>
      <div><div class="lbl">创建时间</div><div class="val">${createdAt}</div></div>
      <div><div class="lbl">完成时间</div><div class="val">${updatedAt}</div></div>
      <div><div class="lbl">分析耗时</div><div class="val" style="font-size:15px;font-weight:600;color:#f1f5f9;">${duration}</div></div>
      <div><div class="lbl">识别方式</div><div class="val" style="color:#60a5fa;">${task.preprocess_options?.recognition_mode === 'image' ? '图像 LLM 识别' : '视频 LLM 识别'}</div></div>
      <div><div class="lbl">Token 用量</div><div class="val">输入 ${task.input_tokens || 0} / 输出 ${task.output_tokens || 0}</div></div>
    </div>
  </div>

  <div id="sec-dim" style="border-top:1px solid rgba(255,255,255,0.06);padding:24px 50px 0;margin-bottom:24px;">
    <h3>维度评分详情</h3>
    <div style="display:flex;gap:32px;align-items:flex-start;">
      <div style="flex-shrink:0;width:340px;">
        ${radarDataUrl ? `<img src="${radarDataUrl}" style="width:100%;display:block;" />` : ''}
      </div>
      <div style="flex:1;display:flex;flex-direction:column;gap:0;">
        ${buildScoreBars()}
      </div>
    </div>
  </div>

  <div id="sec-vid" style="border-top:1px solid rgba(255,255,255,0.06);padding:24px 50px 0;margin-bottom:24px;">
    <h3>视频对比</h3>
    <div style="display:flex;gap:16px;">
      <div style="flex:1;background:#000;border-radius:8px;overflow:hidden;border:1px solid rgba(255,255,255,0.08);">
        ${vidAThumb ? `<img src="${vidAThumb}" style="width:100%;display:block;aspect-ratio:16/9;object-fit:contain;" />` : '<div style="aspect-ratio:16/9;display:flex;align-items:center;justify-content:center;color:#64748b;font-size:13px;">视频 A 预览</div>'}
        <div style="padding:8px 12px;font-size:12px;color:#94a3b8;background:rgba(0,0,0,0.5);">视频 A &mdash; ${S(vidAName)}</div>
      </div>
      <div style="flex:1;background:#000;border-radius:8px;overflow:hidden;border:1px solid rgba(255,255,255,0.08);">
        ${vidBThumb ? `<img src="${vidBThumb}" style="width:100%;display:block;aspect-ratio:16/9;object-fit:contain;" />` : '<div style="aspect-ratio:16/9;display:flex;align-items:center;justify-content:center;color:#64748b;font-size:13px;">视频 B 预览</div>'}
        <div style="padding:8px 12px;font-size:12px;color:#94a3b8;background:rgba(0,0,0,0.5);">视频 B &mdash; ${S(vidBName)}</div>
      </div>
    </div>
  </div>

  ${((kfA || kfB) && task.preprocess_options?.recognition_mode !== 'video') ? `
  <div id="sec-kf" style="border-top:1px solid rgba(255,255,255,0.06);padding:24px 50px 0;margin-bottom:24px;">
    <h3>环境要素抽取 (Keyframes)</h3>
    ${kfA ? `<div style="margin-bottom:4px;"><span style="font-size:12px;color:#64748b;">视频 A (${(result?.key_frames_a || []).length} 帧)</span></div><div style="display:flex;gap:10px;flex-wrap:wrap;margin-bottom:20px;">${kfA}</div>` : ''}
    ${kfB ? `<div style="margin-bottom:4px;"><span style="font-size:12px;color:#64748b;">视频 B (${(result?.key_frames_b || []).length} 帧)</span></div><div style="display:flex;gap:10px;flex-wrap:wrap;">${kfB}</div>` : ''}
  </div>
  ` : ''}

  <div id="sec-ai" style="padding:0 50px;${!(kfA || kfB) ? 'border-top:1px solid rgba(255,255,255,0.06);padding-top:24px;' : ''}margin-bottom:20px;">
    <h3>AI 综合分析结论</h3>
    <div class="card" style="font-size:14px;line-height:1.8;color:#cbd5e1;white-space:pre-wrap;margin-bottom:18px;">${S(result?.summary || '等待分析中...')}</div>
    <div style="display:flex;gap:16px;">
      <div style="flex:1;background:rgba(16,185,129,0.06);border:1px solid rgba(16,185,129,0.12);border-radius:10px;padding:18px 22px;">
        <div style="font-size:14px;font-weight:700;color:#10b981;margin-bottom:10px;">相似点</div>
        <ul style="margin:0;padding-left:18px;font-size:13px;color:#a7f3d0;line-height:1.9;">${(result?.similar_points || []).map((p: string) => `<li>${S(p)}</li>`).join('') || '<li style="color:#64748b;">暂无</li>'}</ul>
      </div>
      <div style="flex:1;background:rgba(245,158,11,0.06);border:1px solid rgba(245,158,11,0.12);border-radius:10px;padding:18px 22px;">
        <div style="font-size:14px;font-weight:700;color:#f59e0b;margin-bottom:10px;">差异点</div>
        <ul style="margin:0;padding-left:18px;font-size:13px;color:#fde68a;line-height:1.9;">${(result?.difference_points || []).map((p: string) => `<li>${S(p)}</li>`).join('') || '<li style="color:#64748b;">暂无</li>'}</ul>
      </div>
    </div>
  </div>

  <div id="sec-footer" style="padding:0 50px;border-top:1px solid rgba(255,255,255,0.06);padding-top:16px;font-size:11px;color:#475569;text-align:center;">
    EnvMatch AI &mdash; 视频环境相似度对比平台
  </div>

</div>`;

    // 4. Render off-screen
    const container = document.createElement('div');
    container.id = 'export-container-root';
    container.style.cssText = 'position:absolute;left:-9999px;top:0;';
    container.innerHTML = html;
    document.body.appendChild(container);

    try {
      const root = container.querySelector('#eroot') as HTMLElement;
      await waitForImages(root);
      await new Promise(r => setTimeout(r, 1200));

      // 5. Capture each section individually to avoid cutting content at page breaks
      const secIds = ['sec-header', 'sec-score', 'sec-dim', 'sec-vid', 'sec-kf', 'sec-ai', 'sec-footer'];
      const imgWidth = 960;

      const secImages: { dataUrl: string; w: number; h: number }[] = [];
      for (const sid of secIds) {
        const el = root.querySelector(`#${sid}`) as HTMLElement;
        if (!el) continue;
        const rect = el.getBoundingClientRect();
        const h = Math.ceil(rect.height) + 2;
        const dataUrl = await domToPng(el, { scale: 2, backgroundColor: '#0f172a', width: imgWidth, height: h });
        secImages.push({ dataUrl, w: imgWidth, h });
      }

      // 6. Compose PDF with page-break logic
      const pdf = new jsPDF('p', 'px', 'a4');
      const pdfW = pdf.internal.pageSize.getWidth();
      const pdfH = pdf.internal.pageSize.getHeight();
      const scaler = pdfW / imgWidth;

      let y = 0;
      for (let i = 0; i < secImages.length; i++) {
        const scaledH = secImages[i].h * scaler;

        // Start new page if section doesn't fit on current page
        if (i > 0 && y + scaledH > pdfH - 10) {
          pdf.addPage();
          pdf.setFillColor(15, 23, 42);
          pdf.rect(0, 0, pdfW, pdfH, 'F');
          y = 0;
        }

        // Dark background for first page too
        if (i === 0) {
          pdf.setFillColor(15, 23, 42);
          pdf.rect(0, 0, pdfW, pdfH, 'F');
        }

        pdf.addImage(secImages[i].dataUrl, 'PNG', 0, y, pdfW, scaledH);
        y += scaledH;
      }

      pdf.save(`EnvMatch_Report_${task.task_name || id}.pdf`);

    } catch (err) {
      console.error('Export Error:', err);
      alert(`导出失败: ${err}`);
    } finally {
      if (container.parentNode) document.body.removeChild(container);
      setExporting(false);
    }
  };

  if (loading) return <div className="p-8 text-center text-muted-foreground"><Loader2 className="w-8 h-8 animate-spin mx-auto mb-2" />加载中...</div>;
  if (!data || !data.task) return <div className="p-8 text-center text-red-500">获取任务详情失败</div>;

  const { task, result } = data;
  const isFailed = task.status === 'FAILED';
  const dims = result?.dimension_scores || {};

  const ringOption = {
    series: [{
      type: 'gauge',
      startAngle: 90, endAngle: -270,
      pointer: { show: false },
      progress: {
        show: true, overlap: false, roundCap: true,
        itemStyle: { color: task.similarity_score > 70 ? '#10b981' : task.similarity_score > 40 ? '#f59e0b' : '#ef4444' }
      },
      axisLine: { lineStyle: { width: 8, color: [[1, 'rgba(255,255,255,0.05)']] } },
      splitLine: { show: false }, axisTick: { show: false }, axisLabel: { show: false },
      data: [{ value: task.similarity_score || 0 }],
      detail: { show: false }
    }]
  };

  const radarOption = {
    backgroundColor: 'transparent',
    radar: {
      indicator: [
        { name: '光照/天气', max: 100 },
        { name: '建筑风格', max: 100 },
        { name: '固定设施', max: 100 },
        { name: '植被绿化', max: 100 },
        { name: '地面材质', max: 100 }
      ],
      shape: 'circle', splitNumber: 4,
      axisName: { color: '#94a3b8', fontSize: 10 },
      splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.1)' } },
      splitArea: { show: false },
      axisLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.1)' } }
    },
    series: [{
      type: 'radar',
      data: [{
        value: [
          dims.lighting_weather || 0,
          dims.architecture || 0,
          dims.facilities || 0,
          dims.vegetation || 0,
          dims.road_surface || 0
        ],
        symbol: 'none',
        itemStyle: { color: '#10b981' },
        areaStyle: { color: 'rgba(16, 185, 129, 0.2)' },
        lineStyle: { width: 2 }
      }]
    }]
  };

  return (
    <div className="p-6 h-full flex flex-col overflow-hidden bg-background">
      <div className="flex items-center justify-between mb-6 shrink-0">
        <h3 className="text-xl font-bold flex items-center gap-2">
          <button onClick={() => navigate('/tasks')} className="p-1.5 hover:bg-muted rounded-md transition-colors"><ArrowLeft className="w-5 h-5" /></button>
          结果详情 <span className="text-sm text-muted-foreground font-normal">/ {id}</span>
        </h3>
        <button onClick={handleExport} disabled={exporting} className="bg-primary/20 text-primary hover:bg-primary/30 px-3 py-1.5 rounded-md text-sm flex items-center gap-2 transition-colors">
          {exporting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />} 导出报告 (PDF)
        </button>
      </div>

      <div ref={reportRef} className="flex gap-6 flex-1 min-h-0 overflow-y-auto custom-scrollbar p-2">
        {/* Left: Scoring */}
        <div className="w-[280px] flex flex-col items-center shrink-0">
          <h4 className="text-sm font-medium w-full mb-4">相似度评分</h4>
          <div className="relative w-44 h-44 shrink-0">
            <ReactECharts ref={ringChartRef} option={ringOption} style={{ height: '100%', width: '100%' }} />
            <div className="absolute inset-0 flex flex-col items-center justify-center">
              <span className={cn("text-4xl font-bold", isFailed && !task.similarity_score ? "text-muted-foreground" : "text-foreground")}>
                {task.similarity_score?.toFixed(1) || '--'}
                {task.similarity_score > 0 && <span className="text-xl">%</span>}
              </span>
              <span className="text-xs text-primary mt-1">{isFailed ? '暂无评分' : '综合结果'}</span>
            </div>
          </div>

          <h4 className="text-sm font-medium w-full mt-8 mb-4">评分分布</h4>
          <div className="w-full h-[240px] shrink-0 mb-4">
            <ReactECharts ref={radarChartRef} option={radarOption} style={{ height: '100%' }} />
          </div>
          <div className="w-full space-y-2">
            <ScoreRow label="光照/天气" val={dims.lighting_weather} />
            <ScoreRow label="建筑风格" val={dims.architecture} />
            <ScoreRow label="固定设施" val={dims.facilities} />
            <ScoreRow label="植被绿化" val={dims.vegetation} />
            <ScoreRow label="地面材质" val={dims.road_surface} />
          </div>
        </div>

        {/* Middle: Video & Content */}
        <div className="flex-1 min-w-0 space-y-6">
          <div>
            <h4 className="text-sm font-medium mb-4">视频对比（关键帧对齐）</h4>
            <div className="flex gap-4">
              <div className="flex-1 bg-black aspect-video rounded-lg overflow-hidden border border-border/50">
                <video className="w-full h-full object-contain" controls src={`http://localhost:8888/storage/${(task.video_a_path || '').split('\\').pop()?.split('/').pop()}`} />
              </div>
              <div className="flex-1 bg-black aspect-video rounded-lg overflow-hidden border border-border/50">
                <video className="w-full h-full object-contain" controls src={`http://localhost:8888/storage/${(task.video_b_path || '').split('\\').pop()?.split('/').pop()}`} />
              </div>
            </div>
          </div>

          {task.preprocess_options?.recognition_mode !== 'video' && (
            <div>
              <h4 className="text-sm font-medium mb-4">环境要素抽取 (Keyframes)</h4>
              <div className="space-y-2">
                <div className="flex gap-2 overflow-x-auto pb-2 custom-scrollbar">
                  {result?.key_frames_a?.map((kf: string, i: number) => {
                    const url = `http://localhost:8888/${kf.replace(/\\/g, '/').replace(/^storage\//, 'storage/')}`;
                    return (
                      <img key={i} src={url}
                        crossOrigin="anonymous"
                        className="w-24 aspect-video object-cover rounded border border-border hover:border-primary cursor-pointer transition-all"
                        onClick={() => {
                          setSelectedImg(url);
                          setZoom(100);
                          setPos({ x: 0, y: 0 });
                        }} />
                    );
                  })}
                </div>
                <div className="flex gap-2 overflow-x-auto pb-2 custom-scrollbar">
                  {result?.key_frames_b?.map((kf: string, i: number) => {
                    const url = `http://localhost:8888/${kf.replace(/\\/g, '/').replace(/^storage\//, 'storage/')}`;
                    return (
                      <img key={i} src={url}
                        crossOrigin="anonymous"
                        className="w-24 aspect-video object-cover rounded border border-border hover:border-primary cursor-pointer transition-all"
                        onClick={() => {
                          setSelectedImg(url);
                          setZoom(100);
                          setPos({ x: 0, y: 0 });
                        }} />
                    );
                  })}
                </div>
              </div>
            </div>
          )}

          <div>
            <h4 className="text-sm font-medium mb-4">AI 综合分析结论</h4>
            <div className={cn("text-sm leading-relaxed p-4 rounded-lg border", isFailed ? "bg-red-500/5 text-red-500 border-red-500/10" : "bg-muted/20 border-border/50")}>
              <div className="whitespace-pre-wrap">{result?.summary || "等待分析中..."}</div>
            </div>
            <div className="grid grid-cols-2 gap-4 mt-4">
              <div className="bg-emerald-500/5 border border-emerald-500/10 p-3 rounded-lg">
                <h5 className="text-xs font-bold text-emerald-500 mb-2">相似点</h5>
                <ul className="text-xs space-y-1 list-disc list-inside opacity-80">{result?.similar_points?.map((p: string, i: number) => <li key={i}>{p}</li>)}</ul>
              </div>
              <div className="bg-amber-500/5 border border-amber-500/10 p-3 rounded-lg">
                <h5 className="text-xs font-bold text-amber-500 mb-2">差异点</h5>
                <ul className="text-xs space-y-1 list-disc list-inside opacity-80">{result?.difference_points?.map((p: string, i: number) => <li key={i}>{p}</li>)}</ul>
              </div>
            </div>
          </div>
        </div>

        {/* Right: Basics (Redesigned according to mockup) */}
        <div className="w-[300px] flex flex-col pl-4 border-l border-border/30 shrink-0">
          <h4 className="text-sm font-bold mb-4 text-foreground/90">基本信息</h4>

          <div className="bg-[#0b0f1a] rounded-xl p-5 border border-white/5 space-y-5 shadow-2xl">
            {/* Top Section: Task Info */}
            <div className="space-y-4">
              <div className="space-y-1">
                <div className="text-[11px] text-muted-foreground/60">任务名称:</div>
                <div className="text-lg font-bold text-foreground tracking-tight">{task.task_name}</div>
              </div>

              <div className="space-y-1">
                <div className="text-[11px] text-muted-foreground/60">创建时间:</div>
                <div className="text-[13px] font-medium text-foreground/90">{new Date(task.created_at).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '/')}</div>
              </div>

              <div className="space-y-1">
                <div className="text-[11px] text-muted-foreground/60">完成时间:</div>
                <div className="text-[13px] font-medium text-foreground/90">{task.updated_at ? new Date(task.updated_at).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '/') : '--'}</div>
              </div>

              <div className="space-y-1">
                <div className="text-[11px] text-muted-foreground/60">耗时:</div>
                <div className="text-[15px] font-bold text-foreground">
                  {task.updated_at ? `${Math.round((new Date(task.updated_at).getTime() - new Date(task.created_at).getTime()) / 1000)} 秒` : '--'}
                </div>
              </div>

              <div className="space-y-1">
                <div className="text-[11px] text-muted-foreground/60">模型版本:</div>
                <div className="text-[13px] font-bold text-foreground font-mono">{task.model_id}</div>
              </div>

              <div className="space-y-1">
                <div className="text-[11px] text-muted-foreground/60">识别方式:</div>
                <div className="flex items-center gap-1.5 text-blue-400 font-medium text-[13px]">
                  {task.preprocess_options?.recognition_mode === 'image' ? (
                    <>
                      <Image className="w-4 h-4" /> 图像 LLM 识别
                    </>
                  ) : (
                    <>
                      <Video className="w-4 h-4" /> 视频 LLM 识别
                    </>
                  )}
                </div>
              </div>
            </div>

            <div className="h-px bg-white/5" />

            {/* Middle Section: Video Specs */}
            <div className="space-y-2.5">
              <div className="flex justify-between items-center text-[12px]">
                <span className="text-muted-foreground/60">视频时长:</span>
                <span className="text-foreground font-medium">{task.video_a_duration?.toFixed(1)}s / {task.video_b_duration?.toFixed(1)}s</span>
              </div>
              <div className="flex justify-between items-center text-[12px]">
                <span className="text-muted-foreground/60">分辨率:</span>
                <span className="text-foreground font-medium">{task.video_a_resolution} / {task.video_b_resolution}</span>
              </div>
              <div className="flex justify-between items-center text-[12px]">
                <span className="text-muted-foreground/60">文件大小:</span>
                <span className="text-foreground font-medium">{task.video_a_size}MB / {task.video_b_size}MB</span>
              </div>
              <div className="flex justify-between items-center text-[12px]">
                <span className="text-muted-foreground/60">关键帧数量:</span>
                <span className="text-foreground font-medium">{(result?.key_frames_a || []).length} / {(result?.key_frames_b || []).length}</span>
              </div>
            </div>

            <div className="h-px bg-white/5" />

            {/* Bottom Section: Token Usage */}
            <div className="space-y-2.5">
              <div className="flex justify-between items-center text-[12px]">
                <div className="flex items-center gap-1.5">
                  <div className="w-1 h-1 rounded-full bg-blue-500/50" />
                  <span className="text-muted-foreground/60">输入 Token:</span>
                </div>
                <span className="text-foreground font-bold font-mono">{task.input_tokens || 0}</span>
              </div>
              <div className="flex justify-between items-center text-[12px]">
                <div className="flex items-center gap-1.5">
                  <div className="w-1 h-1 rounded-full bg-emerald-500/50" />
                  <span className="text-muted-foreground/60">输出 Token:</span>
                </div>
                <span className="text-foreground font-bold font-mono">{task.output_tokens || 0}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <AnimatePresence>
        {selectedImg && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 bg-black/80 backdrop-blur-md z-[100000] flex items-center justify-center p-8"
            onClick={() => setSelectedImg(null)}
          >
            <motion.div
              ref={previewRef}
              initial={{ scale: 0.9, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.9, opacity: 0 }}
              className="bg-[#1a1f2e]/95 border border-white/10 rounded-2xl w-full max-w-5xl h-[85vh] flex flex-col overflow-hidden shadow-2xl"
              onClick={(e) => e.stopPropagation()}
            >
              {/* Header */}
              <div className="flex items-center justify-between px-6 py-4 border-b border-white/5">
                <div className="flex items-center gap-3">
                  <button
                    onClick={toggleFullScreen}
                    className="p-2 bg-blue-500/10 rounded-lg hover:bg-blue-500/20 transition-colors cursor-pointer group"
                    title="全屏展示"
                  >
                    <Scan className="w-5 h-5 text-blue-400 group-hover:scale-110 transition-transform" />
                  </button>
                  <div>
                    <div className="text-sm font-bold text-white">关键帧预览</div>
                    <div className="text-[10px] text-white/40 tracking-wider">KEYFRAME DETAIL VIEW</div>
                  </div>
                </div>

                <div className="flex items-center gap-4">
                  <div className="flex items-center gap-2 bg-white/5 px-3 py-1 rounded-full border border-white/5">
                    <button
                      onClick={() => setZoom(z => Math.max(20, z - 10))}
                      className="p-1 hover:text-white text-white/50 transition-colors"
                    >
                      <Minus className="w-4 h-4" />
                    </button>
                    <span className="text-xs font-mono text-white/80 w-12 text-center">{zoom}%</span>
                    <button
                      onClick={() => setZoom(z => Math.min(500, z + 10))}
                      className="p-1 hover:text-white text-white/50 transition-colors"
                    >
                      <Plus className="w-4 h-4" />
                    </button>
                  </div>
                  <button
                    onClick={() => { setZoom(100); setPos({ x: 0, y: 0 }); }}
                    className="p-2 hover:bg-white/5 rounded-full text-white/50 hover:text-white transition-colors"
                  >
                    <RotateCcw className="w-4 h-4" />
                  </button>
                  <div className="w-px h-4 bg-white/10 mx-1" />
                  <button
                    onClick={() => setSelectedImg(null)}
                    className="p-2 hover:bg-red-500/10 rounded-full text-white/50 hover:text-red-400 transition-colors"
                  >
                    <X className="w-5 h-5" />
                  </button>
                </div>
              </div>

              {/* Content */}
              <div
                className="flex-1 relative overflow-hidden bg-[#0a0d14] flex items-center justify-center cursor-grab active:cursor-grabbing"
                onWheel={(e) => {
                  const delta = e.deltaY > 0 ? -10 : 10;
                  setZoom(z => Math.min(500, Math.max(20, z + delta)));
                }}
              >
                <motion.img
                  src={selectedImg}
                  animate={{
                    scale: zoom / 100,
                    x: pos.x,
                    y: pos.y
                  }}
                  transition={{ type: 'spring', damping: 25, stiffness: 200 }}
                  drag
                  onDrag={(e, info) => {
                    setPos(prev => ({
                      x: prev.x + info.delta.x,
                      y: prev.y + info.delta.y
                    }));
                  }}
                  className="max-w-[90%] max-h-[90%] object-contain shadow-2xl pointer-events-none select-none"
                />
              </div>

              {/* Footer */}
              <div className="px-6 py-4 flex items-center justify-between border-t border-white/5 bg-[#1a1f2e]/50 backdrop-blur-sm">
                <div className="text-[11px] text-white/40 italic flex items-center gap-2">
                  提示: 滚轮可缩放，放大后可鼠标拖动查看详情
                </div>
                <button
                  onClick={() => setSelectedImg(null)}
                  className="px-6 py-2 bg-blue-600 hover:bg-blue-500 text-white rounded-lg text-sm font-medium transition-all shadow-lg shadow-blue-600/20"
                >
                  关闭预览
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function ScoreRow({ label, val }: { label: string; val: number }) {
  return (
    <div className="flex justify-between items-center text-xs">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-bold text-primary">{val || 0}%</span>
    </div>
  );
}
