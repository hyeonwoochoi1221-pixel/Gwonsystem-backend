$ErrorActionPreference='Stop'
$taskWork=$PSScriptRoot
$taskRepo=(Resolve-Path (Join-Path $taskWork '../../../..')).Path
$taskJdk='C:\Program Files\Java\jdk-21.0.10'
if(Test-Path -LiteralPath "$taskWork/cacerts-test"){throw 'Existing test truststore: refuse overwrite'}
& "$taskJdk/bin/javac.exe" -d $taskWork "$taskRepo/docker/dev/VerifySupabaseCa.java" "$taskWork/TrustProbe.java"
if($LASTEXITCODE){throw 'Compilation failed'}
& "$taskJdk/bin/java.exe" -cp $taskWork VerifySupabaseCa "$taskRepo/docker/dev/prod-ca-2021.crt" *> "$taskWork/ca-verification.log"
if($LASTEXITCODE){throw 'CA verification failed'}
Copy-Item -LiteralPath "$taskJdk/lib/security/cacerts" -Destination "$taskWork/cacerts-test"
& "$taskJdk/bin/keytool.exe" -importcert -noprompt -trustcacerts -keystore "$taskWork/cacerts-test" -storepass changeit -alias gwon-supabase-root-2021 -file "$taskRepo/docker/dev/prod-ca-2021.crt" *> "$taskWork/import.log"
if($LASTEXITCODE){throw 'CA import failed'}
& "$taskJdk/bin/java.exe" -cp $taskWork TrustProbe "$taskWork/cacerts-test" "$taskWork/invalid-ca.crt" *> "$taskWork/trust-probe.log"
if($LASTEXITCODE){throw 'Trust probe failed'}
& "$taskJdk/bin/java.exe" -cp $taskWork VerifySupabaseCa "$taskWork/invalid-ca.crt" *> "$taskWork/invalid-ca.log"
if($LASTEXITCODE -eq 0){throw 'Corrupt public CA accepted'}
Write-Output 'PASS public CA import and corruption rejection; system cacerts unchanged'
