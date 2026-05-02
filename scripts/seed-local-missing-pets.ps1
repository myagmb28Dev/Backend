param(
    [string]$BackendBase = "http://127.0.0.1:8081",
    [string]$AuthBase = "http://127.0.0.1:9099",
    [string]$ProjectId = "pogun-local",
    [string]$Email = "playwright-user1@local.dev",
    [string]$Password = "Test1234!",
    [int]$Limit = 30,
    [int]$StartAt = 1
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
$seedDir = Join-Path $repoRoot ".local\seed-missing-pets"
New-Item -ItemType Directory -Force -Path $seedDir | Out-Null

$items = @(
    @{ wiki = "Maltese_dog"; breed = "말티즈"; animalType = "DOG"; gender = "MALE"; age = 3; color = "WHITE"; region = "서울 강남구"; address = "역삼역 3번 출구 인근"; reward = 300000; phone = "010-1000-1001"; desc = "사람을 잘 따르고 얼굴 털이 풍성합니다. 파란 목줄을 착용했습니다."; title = "말티즈를 찾습니다" },
    @{ wiki = "Pomeranian_dog"; breed = "포메라니안"; animalType = "DOG"; gender = "FEMALE"; age = 2; color = "BROWN"; region = "서울 송파구"; address = "잠실새내역 근처 골목"; reward = 250000; phone = "010-1000-1002"; desc = "체구가 작고 털이 둥글게 부풀어 있습니다. 이름을 부르면 반응합니다."; title = "포메라니안을 찾습니다" },
    @{ wiki = "Poodle"; breed = "푸들"; animalType = "DOG"; gender = "MALE"; age = 4; color = "APRICOT"; region = "서울 마포구"; address = "홍대입구역 9번 출구 부근"; reward = 200000; phone = "010-1000-1003"; desc = "곱슬 털과 긴 귀가 특징입니다. 빨간 하네스를 착용했습니다."; title = "푸들을 찾습니다" },
    @{ wiki = "Golden_Retriever"; breed = "골든리트리버"; animalType = "DOG"; gender = "FEMALE"; age = 5; color = "GOLD"; region = "경기 성남시"; address = "정자역 탄천 산책로"; reward = 350000; phone = "010-1000-1004"; desc = "큰 체구에 순한 얼굴입니다. 사람을 잘 따라갑니다."; title = "골든리트리버를 찾습니다" },
    @{ wiki = "Labrador_Retriever"; breed = "래브라도 리트리버"; animalType = "DOG"; gender = "MALE"; age = 6; color = "BLACK"; region = "인천 연수구"; address = "센트럴파크 산책길"; reward = 300000; phone = "010-1000-1005"; desc = "검은 털과 큰 눈이 특징입니다. 노란 목줄을 하고 있습니다."; title = "래브라도 리트리버를 찾습니다" },
    @{ wiki = "Shih_Tzu"; breed = "시츄"; animalType = "DOG"; gender = "FEMALE"; age = 7; color = "WHITE_BROWN"; region = "부산 해운대구"; address = "장산역 근처 공원"; reward = 180000; phone = "010-1000-1006"; desc = "코가 짧고 눈이 큽니다. 분홍 리본을 묶었습니다."; title = "시츄를 찾습니다" },
    @{ wiki = "Chihuahua_(dog_breed)"; breed = "치와와"; animalType = "DOG"; gender = "MALE"; age = 3; color = "TAN"; region = "대전 유성구"; address = "궁동 로데오거리"; reward = 220000; phone = "010-1000-1007"; desc = "귀가 매우 크고 작은 체형입니다. 겁이 많습니다."; title = "치와와를 찾습니다" },
    @{ wiki = "Beagle"; breed = "비글"; animalType = "DOG"; gender = "FEMALE"; age = 4; color = "TRICOLOR"; region = "광주 북구"; address = "전남대 후문 인근"; reward = 210000; phone = "010-1000-1008"; desc = "갈색과 흰색, 검은색이 섞여 있고 코로 냄새를 잘 맡습니다."; title = "비글을 찾습니다" },
    @{ wiki = "Welsh_Corgi"; breed = "웰시코기"; animalType = "DOG"; gender = "MALE"; age = 2; color = "BROWN_WHITE"; region = "대구 수성구"; address = "수성못 산책길"; reward = 260000; phone = "010-1000-1009"; desc = "다리가 짧고 귀가 쫑긋합니다. 엉덩이 털이 둥글게 보입니다."; title = "웰시코기를 찾습니다" },
    @{ wiki = "French_Bulldog"; breed = "프렌치 불도그"; animalType = "DOG"; gender = "FEMALE"; age = 5; color = "FAWN"; region = "울산 남구"; address = "삼산동 카페거리"; reward = 240000; phone = "010-1000-1010"; desc = "납작한 코와 박쥐 귀가 특징입니다. 회색 옷을 입었습니다."; title = "프렌치 불도그를 찾습니다" },
    @{ wiki = "Bichon_Frise"; breed = "비숑 프리제"; animalType = "DOG"; gender = "MALE"; age = 1; color = "WHITE"; region = "세종시 어진동"; address = "중앙공원 입구"; reward = 230000; phone = "010-1000-1011"; desc = "흰 구름처럼 둥근 털이 특징입니다. 이름은 뭉치입니다."; title = "비숑 프리제를 찾습니다" },
    @{ wiki = "Korean_Jindo"; breed = "진돗개"; animalType = "DOG"; gender = "FEMALE"; age = 6; color = "WHITE"; region = "전남 진도군"; address = "진도읍 시장 근처"; reward = 400000; phone = "010-1000-1012"; desc = "귀가 서 있고 눈매가 또렷합니다. 사람을 경계할 수 있습니다."; title = "진돗개를 찾습니다" },
    @{ wiki = "Siberian_Husky"; breed = "시베리안 허스키"; animalType = "DOG"; gender = "MALE"; age = 3; color = "GRAY_WHITE"; region = "강원 춘천시"; address = "공지천 산책로"; reward = 320000; phone = "010-1000-1013"; desc = "얼굴 마스크 무늬가 뚜렷하고 눈빛이 강합니다."; title = "시베리안 허스키를 찾습니다" },
    @{ wiki = "Samoyed_(dog)"; breed = "사모예드"; animalType = "DOG"; gender = "FEMALE"; age = 4; color = "WHITE"; region = "제주 제주시"; address = "노형동 근린공원"; reward = 330000; phone = "010-1000-1014"; desc = "하얗고 미소 짓는 듯한 얼굴입니다. 털이 매우 풍성합니다."; title = "사모예드를 찾습니다" },
    @{ wiki = "Border_Collie"; breed = "보더 콜리"; animalType = "DOG"; gender = "MALE"; age = 5; color = "BLACK_WHITE"; region = "경기 고양시"; address = "일산호수공원 서문"; reward = 280000; phone = "010-1000-1015"; desc = "눈빛이 매우 영리하고 검정 흰색 털무늬가 뚜렷합니다."; title = "보더 콜리를 찾습니다" },
    @{ wiki = "Persian_cat"; breed = "페르시안"; animalType = "CAT"; gender = "FEMALE"; age = 4; color = "WHITE"; region = "서울 서초구"; address = "교대역 10번 출구 인근"; reward = 250000; phone = "010-2000-2001"; desc = "납작한 코와 긴 털이 특징입니다. 눈물이 자주 맺힙니다."; title = "페르시안 고양이를 찾습니다" },
    @{ wiki = "Siamese_cat"; breed = "샴"; animalType = "CAT"; gender = "MALE"; age = 3; color = "CREAM_BROWN"; region = "서울 용산구"; address = "이태원역 뒷골목"; reward = 240000; phone = "010-2000-2002"; desc = "파란 눈과 짙은 얼굴 포인트가 특징입니다. 낯가림이 있습니다."; title = "샴 고양이를 찾습니다" },
    @{ wiki = "Russian_Blue"; breed = "러시안 블루"; animalType = "CAT"; gender = "FEMALE"; age = 2; color = "GRAY"; region = "경기 수원시"; address = "광교호수공원 북측"; reward = 260000; phone = "010-2000-2003"; desc = "회색 털과 초록빛 눈이 특징입니다. 조용한 편입니다."; title = "러시안 블루를 찾습니다" },
    @{ wiki = "Ragdoll"; breed = "랙돌"; animalType = "CAT"; gender = "MALE"; age = 5; color = "WHITE_BROWN"; region = "인천 부평구"; address = "부평시장역 근처"; reward = 270000; phone = "010-2000-2004"; desc = "푸른 눈과 큰 체형이 특징입니다. 품에 안기면 힘을 빼는 편입니다."; title = "랙돌을 찾습니다" },
    @{ wiki = "Bengal_cat"; breed = "벵갈"; animalType = "CAT"; gender = "FEMALE"; age = 3; color = "BROWN_SPOTTED"; region = "부산 수영구"; address = "광안리 해변 뒤편"; reward = 300000; phone = "010-2000-2005"; desc = "표범 같은 점무늬가 있고 활동량이 많습니다."; title = "벵갈 고양이를 찾습니다" },
    @{ wiki = "Scottish_Fold"; breed = "스코티시 폴드"; animalType = "CAT"; gender = "MALE"; age = 2; color = "GRAY_WHITE"; region = "대전 서구"; address = "둔산동 로데오거리"; reward = 220000; phone = "010-2000-2006"; desc = "접힌 귀와 동그란 얼굴이 특징입니다. 작은 방울 목걸이를 했습니다."; title = "스코티시 폴드를 찾습니다" },
    @{ wiki = "Maine_Coon"; breed = "메인쿤"; animalType = "CAT"; gender = "FEMALE"; age = 6; color = "BROWN_TABBY"; region = "광주 서구"; address = "상무지구 공원"; reward = 320000; phone = "010-2000-2007"; desc = "체구가 크고 귀 끝 털이 올라와 있습니다."; title = "메인쿤을 찾습니다" },
    @{ wiki = "Norwegian_Forest_cat"; breed = "노르웨이 숲"; animalType = "CAT"; gender = "MALE"; age = 4; color = "BROWN_WHITE"; region = "대구 중구"; address = "동성로 인근"; reward = 290000; phone = "010-2000-2008"; desc = "목둘레 털이 풍성하고 얼굴이 삼각형에 가깝습니다."; title = "노르웨이 숲 고양이를 찾습니다" },
    @{ wiki = "British_Shorthair"; breed = "브리티시 숏헤어"; animalType = "CAT"; gender = "FEMALE"; age = 5; color = "BLUE_GRAY"; region = "울산 북구"; address = "호계역 주변"; reward = 230000; phone = "010-2000-2009"; desc = "볼살이 도톰하고 회색 털이 짧고 촘촘합니다."; title = "브리티시 숏헤어를 찾습니다" },
    @{ wiki = "Sphynx_cat"; breed = "스핑크스"; animalType = "CAT"; gender = "MALE"; age = 3; color = "PINK_GRAY"; region = "세종시 나성동"; address = "중심상가 앞"; reward = 340000; phone = "010-2000-2010"; desc = "털이 거의 없고 주름이 많은 얼굴입니다. 추위를 많이 탑니다."; title = "스핑크스를 찾습니다" },
    @{ wiki = "Turkish_Angora"; breed = "터키시 앙고라"; animalType = "CAT"; gender = "FEMALE"; age = 2; color = "WHITE"; region = "제주 서귀포시"; address = "중문관광단지 산책길"; reward = 280000; phone = "010-2000-2011"; desc = "하얀 실크 같은 털과 날렵한 얼굴형이 특징입니다."; title = "터키시 앙고라를 찾습니다" },
    @{ wiki = "American_Shorthair"; breed = "아메리칸 숏헤어"; animalType = "CAT"; gender = "MALE"; age = 4; color = "SILVER_TABBY"; region = "경기 용인시"; address = "수지구청역 인근"; reward = 210000; phone = "010-2000-2012"; desc = "은색 줄무늬와 동그란 눈이 특징입니다. 겁이 많습니다."; title = "아메리칸 숏헤어를 찾습니다" },
    @{ wiki = "Abyssinian_cat"; breed = "아비시니안"; animalType = "CAT"; gender = "FEMALE"; age = 3; color = "RUDDY"; region = "경북 포항시"; address = "영일대 해수욕장 뒤편"; reward = 260000; phone = "010-2000-2013"; desc = "큰 귀와 날씬한 체형, 따뜻한 갈색 털빛이 특징입니다."; title = "아비시니안을 찾습니다" },
    @{ wiki = "Birman"; breed = "버먼"; animalType = "CAT"; gender = "MALE"; age = 5; color = "CREAM_BROWN"; region = "충북 청주시"; address = "성안길 입구"; reward = 275000; phone = "010-2000-2014"; desc = "파란 눈과 흰 양말 무늬 발이 특징입니다."; title = "버먼 고양이를 찾습니다" },
    @{ wiki = "Exotic_Shorthair"; breed = "엑조틱 숏헤어"; animalType = "CAT"; gender = "FEMALE"; age = 4; color = "WHITE_ORANGE"; region = "전북 전주시"; address = "전주 한옥마을 입구"; reward = 235000; phone = "010-2000-2015"; desc = "납작한 코와 짧은 털, 동그란 얼굴이 특징입니다."; title = "엑조틱 숏헤어를 찾습니다" }
)

function Invoke-JsonPost {
    param(
        [string]$Url,
        [hashtable]$Body,
        [hashtable]$Headers = @{}
    )

    return Invoke-RestMethod -Method Post -Uri $Url -Headers $Headers -ContentType "application/json; charset=utf-8" -Body ($Body | ConvertTo-Json -Depth 8)
}

function Get-LocalUserIdToken {
    $signinUrl = "$AuthBase/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fake-api-key"
    $signupUrl = "$AuthBase/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key"
    $payload = @{
        email = $Email
        password = $Password
        returnSecureToken = $true
    }

    try {
        $login = Invoke-JsonPost -Url $signinUrl -Body $payload
        return $login.idToken
    } catch {
        $responseText = $_.ErrorDetails.Message
        if (-not $responseText -or $responseText -notmatch "EMAIL_NOT_FOUND") {
            throw
        }
    }

    Invoke-JsonPost -Url $signupUrl -Body $payload | Out-Null
    $login = Invoke-JsonPost -Url $signinUrl -Body $payload
    return $login.idToken
}

function Ensure-BackendUserReady {
    param([string]$IdToken)

    $headers = @{ Authorization = "Bearer $IdToken" }
    $login = Invoke-JsonPost -Url "$BackendBase/api/auth/login" -Body @{ firebaseIdToken = $IdToken }
    if ($login.data.id -or $login.data.registrationStatus -ne "PENDING_ONBOARDING") {
        return $IdToken
    }

    Invoke-JsonPost -Url "$BackendBase/api/auth/onboarding/complete" -Headers $headers -Body @{
        x = 127.1086228
        y = 37.4012191
    } | Out-Null
    return $IdToken
}

function Get-WikipediaImageUrl {
    param([string]$Title)

    $encodedTitle = [System.Uri]::EscapeDataString($Title)
    $headers = @{ "User-Agent" = "PogunLocalSeeder/1.0 (local test)" }
    $summary = $null
    for ($attempt = 1; $attempt -le 4; $attempt++) {
        try {
            $summary = Invoke-RestMethod -Method Get -Headers $headers -Uri "https://en.wikipedia.org/api/rest_v1/page/summary/$encodedTitle"
            break
        } catch {
            if ($attempt -ge 4) {
                throw
            }
            Start-Sleep -Seconds (2 * $attempt)
        }
    }
    $candidates = @(
        $summary.originalimage.source,
        $summary.thumbnail.source
    ) | Where-Object { $_ }

    foreach ($candidate in $candidates) {
        $lower = $candidate.ToLowerInvariant()
        if ($lower -match "\.(jpg|jpeg|png|gif)(\?|$)") {
            return $candidate
        }
    }

    throw "Wikipedia image not found for $Title"
}

function Save-RemoteImage {
    param(
        [string]$Url,
        [string]$BaseName
    )

    $uri = [System.Uri]$Url
    $extension = [System.IO.Path]::GetExtension($uri.AbsolutePath)
    if ([string]::IsNullOrWhiteSpace($extension)) {
        $extension = ".jpg"
    }
    $targetPath = Join-Path $seedDir ($BaseName + $extension)
    Invoke-WebRequest -Method Get -Headers @{ "User-Agent" = "PogunLocalSeeder/1.0 (local test)" } -Uri $Url -OutFile $targetPath
    return $targetPath
}

function New-MultipartNoticeRequest {
    param(
        [hashtable]$Item,
        [string]$ImagePath,
        [string]$IdToken,
        [int]$Index
    )

    $requestDto = [ordered]@{
        title = $Item.title
        animalType = $Item.animalType
        breed = $Item.breed
        gender = $Item.gender
        age = $Item.age
        color = $Item.color
        description = $Item.desc
        missingDate = [DateTimeOffset]::UtcNow.AddDays(-($Index % 14 + 1)).ToString("o")
        missingRegion = $Item.region
        missingAddress = $Item.address
        rewardAmount = $Item.reward
        contactPhone = $Item.phone
        status = "OPEN"
    }

    $json = $requestDto | ConvertTo-Json -Depth 8
    $client = New-Object System.Net.Http.HttpClient
    try {
        $client.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $IdToken)
        $content = New-Object System.Net.Http.MultipartFormDataContent

        $jsonContent = New-Object System.Net.Http.StringContent($json, [System.Text.Encoding]::UTF8, "application/json")
        $content.Add($jsonContent, "request")

        $stream = [System.IO.File]::OpenRead($ImagePath)
        try {
            $fileContent = New-Object System.Net.Http.StreamContent($stream)
            $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("image/jpeg")
            $content.Add($fileContent, "images", [System.IO.Path]::GetFileName($ImagePath))

            $response = $client.PostAsync("$BackendBase/api/missing-pets", $content).GetAwaiter().GetResult()
            $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            if (-not $response.IsSuccessStatusCode) {
                throw "Notice upload failed: $($response.StatusCode) $body"
            }
            return $body
        } finally {
            $stream.Close()
        }
    } finally {
        $client.Dispose()
    }
}

$idToken = Get-LocalUserIdToken
Ensure-BackendUserReady -IdToken $idToken | Out-Null

$resolvedLimit = [Math]::Min([Math]::Max($Limit, 1), $items.Count)
$startIndex = [Math]::Min([Math]::Max($StartAt, 1), $items.Count) - 1
$results = @()
for ($i = $startIndex; $i -lt $resolvedLimit; $i++) {
    $item = $items[$i]
    $baseName = "{0:D2}-{1}" -f ($i + 1), $item.wiki.Replace("(", "").Replace(")", "").Replace("_", "-")
    Write-Host ("[{0}/{1}] {2}" -f ($i + 1), $resolvedLimit, $item.breed)
    $imageUrl = Get-WikipediaImageUrl -Title $item.wiki
    $imagePath = Save-RemoteImage -Url $imageUrl -BaseName $baseName
    $body = New-MultipartNoticeRequest -Item $item -ImagePath $imagePath -IdToken $idToken -Index $i
    $results += $body
    Start-Sleep -Milliseconds 1200
}

$resultFile = Join-Path $seedDir "seed-results.jsonl"
[System.IO.File]::WriteAllLines($resultFile, $results, [System.Text.UTF8Encoding]::new($false))
Write-Host ""
Write-Host "Seed complete: $resultFile"
