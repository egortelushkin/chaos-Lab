1. В первом терминале запусти демо-сервис:

cd "C:\Users\Home\Desktop\java projectss\chaosLib"                                                                                                               
./gradlew :chaos-demo:bootRun

2. Во втором терминале проверь, что showcase-эндпоинт жив:

Invoke-RestMethod http://localhost:8080/demo/showcase | ConvertTo-Json -Depth 5 

3. Запусти сам chaos-эксперимент (быстрый режим, ~12 секунд):

$run = Invoke-RestMethod -Method Post "http://localhost:8080/demo/showcase/run?mode=quick"                                                                       
$run | ConvertTo-Json -Depth 8    

4. Как понять результат:

- $run.status — PASS или FAIL
- $run.resilienceScore — итоговый score
- $run.errorRate, $run.p95LatencyMs — общие метрики
- $run.phases — метрики по фазам baseline/fault/recovery
- $run.failedInvariants — что именно завалилось (если FAIL)
- $run.reportPath, $run.metadataPath — полные пути к артефактам

Пример удобного просмотра фаз:

$run.phases | Format-Table phaseName,phaseType,totalOperations,errorRate,p95LatencyMs,avgLatencyMs

5. Посмотреть детальный отчёт:

Get-Content $run.reportPath | ConvertFrom-Json | ConvertTo-Json -Depth 20                                                                                        
Get-Content $run.metadataPath | ConvertFrom-Json | ConvertTo-Json -Depth 20

Что должно произойти по смыслу:
- fault: включаются более жёсткие chaos-сценарии,
- recovery: возврат к мягкому сценарию.
