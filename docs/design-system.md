# DarkIO

En tommelfinger-først hjemskærm til Android. Mørk og teknisk som en terminal, med lidt leg i farverne.

DarkIO er en personlig launcher til en Pixel 10 (Android 16). Designsystemet her er kilden til farver, typografi, afstande og komponenter. Mockups ligger i skærmdesignet *DarkIO – skærmdesign v1*, kravene i *DarkIO – kravspecifikation v1*.

## Principper

1. **Kig øverst, tryk nederst.** Øverste halvdel af skærmen er kun til at kigge på: ur, dashboard-kort, overskrifter. Alt du trykker på dagligt (favoritter, søgning, kategorier, menuer, tilbage) ligger i nederste halvdel. Lister tyngdes mod bunden, og bedste søgeresultat står tættest på feltet.
2. **Én accent ad gangen.** `accent` (lime) markerer det ene, der er valgt eller aktivt i en zone: den valgte kategori, markøren, timen lige nu. To lime-ting i samme zone konkurrerer.
3. **Pastel er til ikoner, ikke til flader.** De fem `pastel-*` farver bruges kun i ikonfelter og små chips med en `bg`-farvet glyf ovenpå. Kort og felter er altid `surface` med `line`-ramme.
4. **Mono er maskinens stemme.** JetBrains Mono bruges til alt, telefonen fortæller dig: labels, tal, stier, kommandoer. Space Grotesk bruges til det, du læser som indhold: ur, overskrifter, app- og aftalenavne.
5. **Ærlig om begrænsninger.** "Skjul" siger, at det ikke er en lås. Et kort uden adgang siger, hvad adgangen bruges til, og at data bliver på telefonen.

## Farver

Kun ét tema: mørkt. Alle tekstpar er målt:

| Par | Kontrast |
| --- | --- |
| `ink` på `bg` / `surface` | 16.6:1 / 15.4:1 |
| `ink-soft` på `bg` | 11.2:1 |
| `ink-muted` på `surface` | 7.0:1 |
| `bg` på `accent` | 15.1:1 |
| `bg` på laveste pastel (`pastel-coral`) | 8.3:1 |
| `danger` på `surface` | 9.1:1 |

`line` (1.3:1) og `line-strong` (1.4–1.6:1) er dekorative. Et felt må aldrig kun kunne kendes på sin ramme: søgefeltet har altid `>` og pladsholdertekst, og en slukket toggle har en `ink-muted` knop (7:1).

## Typografi

- **Space Grotesk** (`display`): `clock` 72/68, `title` 30/36, `metric` 24/26, `item` 17/22, `body` 14/20. Uret og tal har stram spærring (−3px på uret, −0.5px på titler).
- **JetBrains Mono** (`mono`): `input` 15/20, `data` 13/18, `button` 13/16 fed, `label` 11/14. Labels skrives med små bogstaver.
- 11px er det mindste. Under det bliver mono uskarp på telefonen.
- Følg systemets skriftstørrelse (sp i Android). Layouts må vokse i højden, men aldrig klippe tekst.

## Tekst og tone

- Dansk, kort og konkret. Knapper siger, hvad der sker: "vis igen", "giv adgang", "prøv igen".
- Mono-labels bruger terminal-idiomer: `// favoritter`, `~/apps/alle · 64 stk`, `$ darkio --hej_`. Brug dem som krydderi i labels, aldrig i handlinger eller fejltekster, der skal forstås.
- Fejl starter med `err:` og siger, hvad du kan gøre: "err: kunne ikke hente" + knappen "prøv igen".
- Markøren `_` i `accent` er DarkIO's signatur: efter uret, i velkomsten, ved tom tilstand ("frisk dag_"). Højst én pr. skærm.

## Bevægelse og skrivemaskine-effekten

DarkIO bevæger sig kun, når det betyder noget. Skrivemaskine-effekten er signaturen og bruges sparsomt:

- **Hvor:** dagens tip (første gang hjemskærmen vises den dag), velkomsten ved første start (`$ darkio --hej_`) og tomme tilstande som "frisk dag_". Aldrig på ting, du skal bruge hurtigt: app-navne, søgeresultater, tal på kortene, knapper eller fejl.
- **Hvor hurtigt:** 16 ms pr. tegn og højst 1,5 sekund i alt. Længere tekst skrives hurtigere, så den holder sig under 1,5 s.
- **Markøren:** en `accent`-blok på 7 × 15 dp, der blinker hvert 530 ms, når teksten er skrevet færdig. Højst én blinkende markør pr. skærm.
- **Spring over:** et tryk viser hele teksten med det samme.
- **Én gang:** effekten kører kun én gang pr. tekst pr. dag. Kommer du tilbage til hjemskærmen, står teksten der bare.
- **Reducer animationer:** er Androids "Fjern animationer" slået til, vises teksten med det samme, og markøren står stille.
- **Skærmlæser:** hele teksten læses op med det samme. Effekten er kun visuel.

Andre bevægelser holdes korte: 150–200 ms til skuffen og bundark, ingen fjedre eller hop.

## Hjemskærmens sider

Hjemskærmen har tre sider med hver sit fokus: **i dag** (ur, aftale, fokus, tip), **træning** (dagens pas, ugen, skridt, forbrændt) og **byg** (dagens tip i stort format, tidligere tips).

- Nederst på alle sider, i samme rækkefølge: sideprikker, en række med 4 genveje der hører til siden, en fast dock med 4 favoritter (`surface-glass`, `radius-xl`-agtigt 24px hjørne) og søgefeltet.
- Docken og søgefeltet flytter sig aldrig. Kun genvejsrækken og indholdet over den skifter.
- Sideprikker: aktiv side er en 20 × 6 pille i `accent`, andre er 6 × 6 i `line-strong`; hver prik har en 44 × 24 trykflade.
- Højst tre sider. En fjerde side er en swipe mere til det, du skal bruge.

## Din baggrund

Hjemskærmen viser brugerens egen baggrund, fx et familiebillede. Android tegner den bagved; DarkIO læser og gemmer aldrig billedet.

- Over baggrunden ligger en lodret toning: `scrim-top` (80%) bag ur og dato, cirka 35% i midten, så billedet kan ses, og `scrim-bottom` (95%) bag favoritter og søgefelt.
- Kort og søgefelt på hjemskærmen bruger `surface-glass` i stedet for `surface`.
- Tekst direkte på baggrunden er altid `ink` eller `ink-soft`, aldrig `ink-muted`. Uret og datoen har en svag skygge, så de kan læses på lyse billeder.
- Skuffe, søgning, indstillinger og bundark er helt mørke (`bg`). Baggrunden hører kun til hjemskærmen.
- Toningen i toppen kan justeres mellem 60% og 90% under Indstillinger → Baggrund.

## Layout

- Mockups er 390 × 844 og tegnet i dp: 1px i mockup = 1dp i Android.
- Sidemargin `space-5` (20) på hjem, `space-4` (16) i skuffe og indstillinger. Første indhold starter ved `top-inset` (60).
- Appen tegner edge-to-edge (krav i Android 16). Alt trykbart holder sig over navigationsgesture-zonen (`WindowInsets.systemGestures`), og swipe op til skuffen starter over den.
- Kort står i lodret stak med `space-2`–`space-3` imellem. Gitre er 4 kolonner.
- Trykflader er mindst `touch-min`. Brug 48dp, hvor der er plads.

## Ikonografi

- Stregikoner på et 24-gitter: `glyph-stroke` 1.9, runde ender og hjørner, ingen fyld.
- I et ikonfelt: `bg`-farvet glyf på en `pastel-*` flade med `radius-lg`.
- I baren og menuer: `ink-muted` glyf, eller `bg` på `accent`, når den er valgt.
- De rigtige app-ikoner kommer fra telefonen (adaptive icons) og beskæres til `radius-lg`. Pastel-felterne i mockups er kun pladsholdere.
- Ingen emoji.

## Komponenter

| Komponent | Bruges til |
| --- | --- |
| IconTile | App-ikon med navn i gitter, favoritter og lister |
| DashboardCard | Kalender-, trænings-, fokus-, skridt-, forbrændt- og tip-kort |
| SearchField | `>`-søgefeltet nederst på hjem, i skuffe og i søgning |
| CategoryBar | Bundbaren med 7 kategori-ikoner i skuffen |
| Toggle | Tænd/sluk i indstillinger |

## Til udviklingen (Jetpack Compose)

Fonte lægges i appen (`res/font/`, begge er OFL-licenserede) og hentes ikke over nettet. Så holder DarkIO sig til kravet om, at kun vejret går på nettet.

```kotlin
object DarkIOColors {
    val Bg = Color(0xFF0D0D11)
    val Surface = Color(0xFF16161C)
    val Line = Color(0xFF26262F)
    val LineStrong = Color(0xFF33333E)
    val Ink = Color(0xFFEDEDF2)
    val InkSoft = Color(0xFFC4C4CF)
    val InkMuted = Color(0xFFA0A0AE)
    val Accent = Color(0xFFC8F25A)
    val Coral = Color(0xFFFF8A5B)
    val Lilac = Color(0xFFC7B8FF)
    val Sun = Color(0xFFFFD66B)
    val Mint = Color(0xFF8BE3C1)
    val Sky = Color(0xFF8EC5FF)
    val Danger = Color(0xFFFF9E9E)
}

object DarkIOShapes {
    val Sm = RoundedCornerShape(8.dp)
    val Md = RoundedCornerShape(14.dp)
    val Lg = RoundedCornerShape(18.dp)
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
}

val Display = FontFamily(Font(R.font.space_grotesk_semibold, FontWeight.SemiBold), Font(R.font.space_grotesk_regular))
val Mono = FontFamily(Font(R.font.jetbrains_mono_regular), Font(R.font.jetbrains_mono_bold, FontWeight.Bold))

object DarkIOType {
    val Clock = TextStyle(fontFamily = Display, fontSize = 72.sp, lineHeight = 68.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-3).sp)
    val Title = TextStyle(fontFamily = Display, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
    val Metric = TextStyle(fontFamily = Display, fontSize = 24.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
    val Item = TextStyle(fontFamily = Display, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val Body = TextStyle(fontFamily = Display, fontSize = 14.sp, lineHeight = 20.sp)
    val Input = TextStyle(fontFamily = Mono, fontSize = 15.sp, lineHeight = 20.sp)
    val Data = TextStyle(fontFamily = Mono, fontSize = 13.sp, lineHeight = 18.sp)
    val Button = TextStyle(fontFamily = Mono, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
    val Label = TextStyle(fontFamily = Mono, fontSize = 11.sp, lineHeight = 14.sp)
}
```

Byg alle skærme ud fra disse objekter. Er en værdi ikke her, tilføjes den først til designsystemet og derefter til koden.
