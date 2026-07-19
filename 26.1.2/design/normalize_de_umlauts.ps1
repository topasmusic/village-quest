param(
    [string]$Path = (Join-Path $PSScriptRoot '..\src\main\resources\assets\village-quest\lang\de_de.json')
)

$ueExceptions = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
@(
    'aktuell', 'aktuelle', 'aktuellen', 'aktueller', 'aktuelles',
    'anvertrauen', 'aufbauen', 'Baue', 'bauen', 'Bauer', 'Bauern',
    'befeuere', 'blauen', 'braue',
    'dauerhaft', 'dauerhafte', 'dauerhaften', 'dauerhafter',
    'erneuert', 'Feuer', 'Feuerschutz', 'Getreuen', 'Lagerfeuer', 'Lohenfeuer',
    'Mauern', 'neue', 'neuen', 'neuer', 'neuerer', 'neues',
    'Quest', 'Questangebot', 'Questarten', 'Questbefehle', 'Questbrett',
    'Questfortschritt', 'Questmaster', 'Questmeister', 'Questmeisters', 'Quests',
    'Questschritt', 'Questtafel', 'Quellkessel', 'Quenchwasser', 'Querung',
    'Reliktquest', 'scheue', 'Selbstvertrauen', 'Spezialquests', 'Storyquests',
    'Tagesquest', 'Tagesquests', 'trauen', 'Treuer', 'Vertraue', 'Vertrauen',
    'Wochenquest', 'zuerst', 'Zusatzquest'
) | ForEach-Object { [void]$ueExceptions.Add($_) }

$aUmlaut = [string][char]0x00E4
$oUmlaut = [string][char]0x00F6
$uUmlaut = [string][char]0x00FC
$capitalAUmlaut = [string][char]0x00C4
$capitalOUmlaut = [string][char]0x00D6
$capitalUUmlaut = [string][char]0x00DC
$eszett = [string][char]0x00DF

$spellingCorrections = @{
    'Fuesse' = ('F' + $uUmlaut + $eszett + 'e')
    'Fuessen' = ('F' + $uUmlaut + $eszett + 'en')
    'Giessplaetze' = ('Gie' + $eszett + 'pl' + $aUmlaut + 'tze')
    'groessere' = ('gr' + $oUmlaut + $eszett + 'ere')
    'groesserer' = ('gr' + $oUmlaut + $eszett + 'erer')
    'groesseres' = ('gr' + $oUmlaut + $eszett + 'eres')
    'Huegelstrasse' = ('H' + $uUmlaut + 'gelstra' + $eszett + 'e')
    'Huegelstrassen' = ('H' + $uUmlaut + 'gelstra' + $eszett + 'en')
    'Strassenstueck' = ('Stra' + $eszett + 'enst' + $uUmlaut + 'ck')
    'Strassenwaechter' = ('Stra' + $eszett + 'enw' + $aUmlaut + 'chter')
    'suesse' = ('s' + $uUmlaut + $eszett + 'e')
    'suesses' = ('s' + $uUmlaut + $eszett + 'es')
    'Suesswarenladen' = ('S' + $uUmlaut + $eszett + 'warenladen')
    'Waehend' = ('W' + $aUmlaut + 'hrend')
}

$json = [System.IO.File]::ReadAllText((Resolve-Path $Path))
$propertyPattern = '(?m)^(\s*"[^"\r\n]+"\s*:\s*")((?:\\.|[^"\\])*)("[,]?\s*)$'

$normalized = [regex]::Replace($json, $propertyPattern, {
    param($propertyMatch)

    $value = [regex]::Replace($propertyMatch.Groups[2].Value, '\p{L}+', {
        param($wordMatch)

        $word = $wordMatch.Value
        if ($spellingCorrections.ContainsKey($word)) {
            return $spellingCorrections[$word]
        }

        if ($word -ne 'Lavaeimer') {
            $word = $word.Replace('ae', $aUmlaut).Replace('Ae', $capitalAUmlaut)
        }
        $word = $word.Replace('oe', $oUmlaut).Replace('Oe', $capitalOUmlaut)

        if (-not $ueExceptions.Contains($wordMatch.Value)) {
            $word = $word.Replace('ue', $uUmlaut).Replace('Ue', $capitalUUmlaut)
        }
        return $word
    })

    return $propertyMatch.Groups[1].Value + $value + $propertyMatch.Groups[3].Value
})

[System.IO.File]::WriteAllText((Resolve-Path $Path), $normalized, [System.Text.UTF8Encoding]::new($false))
