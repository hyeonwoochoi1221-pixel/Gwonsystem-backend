param([ValidateSet('Probe','Audit')][string]$Mode='Probe')
$ErrorActionPreference='Stop'
$work=$PSScriptRoot
$repo='D:\dev\GWON_System\backend\notice-member-author-backend'
$bin='D:\dev\GWON_System\ai-lab\isolated-tools\postgresql-17.11\pgsql\bin'
$data=Join-Path $work 'pgdata'
if(!(Test-Path "$data\PG_VERSION")){throw 'New test cluster missing'}
& "$bin\pg_ctl.exe" -D $data status
if($LASTEXITCODE -ne 3){throw 'Test cluster not stopped'}
if(netstat -ano -p tcp | Select-String '^\s*TCP\s+\S+:55483\s'){throw 'Port occupied'}
$cp="$work;$repo\target\classes;$work\lib\*"
& 'C:\Program Files\Java\jdk-21.0.10\bin\javac.exe' -cp $cp -d $work "$work\$Mode.java" *> "$work\compile-$Mode.log"
if($LASTEXITCODE){throw 'compile failed'}
$saved=@{}
foreach($name in @('PGHOST','PGHOSTADDR','PGPORT','PGDATABASE','PGUSER','PGPASSWORD','PGSERVICE','PGOPTIONS','PGDATA','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')){
 $saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process');Remove-Item "Env:$name" -ErrorAction SilentlyContinue
}
$attempt=$false
try{
 $attempt=$true
 $arguments='-D "'+$data+'" -p "'+$bin+'\postgres.exe" -l "'+$work+'\server-resume.log" -o "-h 127.0.0.1 -p 55483 -c ssl=off" -w -t 30 start'
 $taskProcess=Start-Process -FilePath "$bin\pg_ctl.exe" -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput "$work\start-resume.log" -RedirectStandardError "$work\start-resume.err"
 $taskProcess.WaitForExit();if($taskProcess.ExitCode){throw 'start failed'}
 & "$bin\pg_isready.exe" -h 127.0.0.1 -p 55483 -U isolated_admin -d postgres *> "$work\ready.log"
 if($LASTEXITCODE){throw 'readiness failed'}
 & 'C:\Program Files\Java\jdk-21.0.10\bin\java.exe' -cp $cp $Mode $work *> "$work\$Mode.log"
 if($LASTEXITCODE){throw 'Probe failed; see probe.log'}
}finally{
 if($attempt){
  $taskProcess=Start-Process -FilePath "$bin\pg_ctl.exe" -ArgumentList ('-D "'+$data+'" -m fast -w -t 30 stop') -WindowStyle Hidden -PassThru -RedirectStandardOutput "$work\stop-resume.log" -RedirectStandardError "$work\stop-resume.err"
  $taskProcess.WaitForExit();$stopCode=$taskProcess.ExitCode
  & "$bin\pg_ctl.exe" -D $data status *> "$work\status-after.log";$statusCode=$LASTEXITCODE
  & "$bin\pg_isready.exe" -h 127.0.0.1 -p 55483 -t 3 *> "$work\ready-after.log"
  "stop=$stopCode status=$statusCode ready=$LASTEXITCODE" | Set-Content "$work\shutdown-result.txt"
 }
 foreach($name in $saved.Keys){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}
}
