# DarkIO – kravspecifikation v1

Nov 8, 2026 · @Zacharias Polonius

En personlig, legende Android-hjemskærm med et dashboard øverst og en app-skuffe med kategorier, søgning, favoritter og skjulte apps. Specifikationen er skrevet, så den kan deles ud til separate bygge- og test-agenter på din PC.

## Mål og acceptkriterie

**Mål:** v1 er godt nok til at erstatte telefonens standard-launcher i daglig brug i én uge uden at du skifter tilbage.

**Det er godt nok, når** alle krav F1–F21 og N1–N11 nedenfor er opfyldt, **og det er bevist ved at** hver krav-række har en grøn automatisk test eller et dokumenteret manuelt testskridt med skærmbillede, og verifikationsrunden (afsnit 7) er gennemført uden åbne fund af typen *kritisk* eller *høj*.

**Målgruppe:** dig selv, på din egen telefon. Appen installeres direkte fra Android Studio (sideload), ikke via Google Play.

**Antagelser jeg har gjort** (ret dem i dokumentet):

- Dashboardet viser ur og dato, næste aftale, dagens træning, fokus (skærmtid og oplåsninger), skridt, forbrændte kalorier og dagens vibe-coding-tip. Hvert kort kan slås fra.
- Telefonen er din Pixel 10 med Android 16. Appen bygges med minSdk 35 og targetSdk 36, så vi ikke skal understøtte gamle Android-versioner.
- Sproget i appen er dansk.

## Skærme og navigation

V1 har seks egne skærme: hjemskærm, app-skuffe, søgning, app-menu, indstillinger og skjulte apps. Notifikationspanelet er Androids eget.

&#91;embedded content: navigation · 5 egne skærme, 2 fra Android\]

Tilbage og Hjem fører altid tilbage til hjemskærmen, og skuffen lukker ved swipe ned. [Skærmdesignet](https://claude.ai/artifact/TrC5Wwg6M2K6WHudMpfkzV) viser hver skærm i detaljer, inklusive fejl- og tomme tilstande.

## Funktionelle krav

21 krav dækker v1. Kolonnen *Bevis* fortæller test-agenten præcis, hvordan kravet skal godkendes: **Unit** = automatisk test af logik, **UI-test** = automatisk Compose-test på emulator, **Manuel** = testskridt på din telefon med skærmbillede.

| ID | Krav | Acceptkriterie | Bevis |
| --- | --- | --- | --- |
| F1 | Appen kan vælges som hjemskærm | Tryk på Hjem efter installation giver valget, og valgt launcher åbner på under 1 sekund | Manuel + UI-test der tjekker at appen svarer på HOME-intent |
| F2 | Ur- og datokort | Viser tid og dansk dato („søndag 8. november“); opdateres hvert minut uden at skærmen skal genindlæses | Unit (formatering) + UI-test |
| F3 | Kalenderkort | Viser næste aftale de næste 24 timer. Uden adgang vises „Giv adgang til kalender“, og intet crasher | Unit + Manuel (giv/afvis adgang) |
| F4 | Dagens træning | Viser dagens pas fra dit program (tirsdag, torsdag, søndag: spinning, romaskine, styrke og RPE). Kan markeres som gennemført. På træningssiden vises ugen (træningsdage, gennemførte pas) og antal pas i træk; andre dage står der „hviledag\_“. Programmet redigeres under Indstillinger og gemmes lokalt | Unit (rigtig dag, hviledag, gennemført nulstilles ved midnat) + UI-test |
| F5 | Skridt | Viser dagens skridt og en bjælke mod dit mål (standard 8.000, kan ændres). Læses fra Health Connect med READ\_STEPS. Uden adgang vises „giv adgang“ | UI-test |
| F6 | Favoritter | 4 faste favoritter i en dock nederst på alle sider, plus en række med 4 genveje der hører til den enkelte side. Begge kan tilføjes, fjernes og omarrangeres | UI-test |
| F7 | App-skuffe | Viser alle apps, der kan startes, som et 4-kolonners gitter med appens rigtige ikon, alfabetisk. Gitteret står mod bunden, så de sidste rækker er tættest på tommelfingeren. Åbner med swipe op eller gitter-knappen på hjemskærmen | UI-test med 200 falske apps |
| F8 | Kategorier | Apps sorteres automatisk i kategorier (Alle, Social, Spil, Foto, Arbejde, Værktøj, Andet). Man skifter kategori med ikoner i en navigationsbar nederst i skuffen, mindst 48 dp høje. Du kan flytte en app til en anden kategori, og valget huskes | Unit (kortlægning) + UI-test |
| F9 | Søgning | Søgefeltet sidder nederst, på hjemskærmen og over kategori-baren. Ved tryk åbner tastaturet, og feltet står lige over det. Resultaterne vokser opad, så bedste hit står tættest på feltet. Matcher dele af navnet, uanset store/små bogstaver og accenter. Enter åbner første hit | Unit (matching inkl. æøå) + UI-test |
| F10 | Skjul apps | En skjult app vises ikke i skuffe, kategori, søgning eller favoritter. Den kan vises igen under Indstillinger → Skjulte apps | Unit + UI-test |
| F11 | Gestures | Swipe op: skuffe. Swipe ned: notifikationer. Swipe vandret: skift side på hjemskærmen. Langt tryk på tom flade: indstillinger. | Manuel på telefon |
| F12 | App-menu | Langt tryk på en app giver: Favorit, Skjul, Flyt kategori, Appinfo, Afinstallér | UI-test |
| F13 | Indstillinger | Accentfarve, kort til/fra, træningsprogram, skridtmål, skjulte apps, nulstil alt | UI-test |
| F14 | Live opdatering | En app du installerer eller fjerner, dukker op eller forsvinder inden for 2 sekunder uden genstart | Manuel (installér/afinstallér en app) |
| F15 | Tommelfinger først | Alt du trykker på dagligt (favoritter, søgning, kategorier, app-menu, tilbage) ligger i nederste halvdel af skærmen. Øverste halvdel er kun til at kigge på: ur, kort og overskrifter | UI-test der tjekker, at alle trykbare elementer på hjem, skuffe og søgning ligger under skærmens midte + manuel test med én hånd |
| F16 | Fokus-kort | Kompakt på hjemskærmen: skærmtid i dag og antal oplåsninger. Ved tryk: forskel mod i går, en søjle pr. time og top 3 apps. Data hentes med UsageStatsManager, når hjemskærmen vises, ikke i baggrunden. Uden adgang vises „giv adgang“, der åbner Android → Adgang til brugsdata. Detaljevisningen har et link til Digital Wellbeing | Unit (summering pr. time, skift ved midnat, sommertid) + manuel: giv og fjern adgang, sammenlign tallet med Digital Wellbeing |
| F17 | Dagens vibe-coding-tip | Viser ét tip om dagen fra en fast liste på mindst 60 tips, der ligger i appen. Samme tip hele dagen, nyt efter midnat, ingen gentagelse før listen er brugt. Langt tryk kopierer tippet. På byg-siden vises tippet stort med en kopier-knap og de to seneste tips | Unit (samme tip hele dagen, skift ved midnat, ingen gentagelse) + UI-test |
| F18 | Forbrændt + genvej til FoodAdvisor | Viser forbrændte kalorier i dag fra Health Connect (READ\_TOTAL\_CALORIES\_BURNED). Hele kortet er et link med ↗ og „+ log mad“, der åbner FoodAdvisor. Indtag og kcal tilbage vises ikke, fordi FoodAdvisor ikke deler ernæring med Health Connect. Er FoodAdvisor ikke installeret, skjules „+ log mad“ | Unit (dagens sum, skift ved midnat) + manuel: tallet matcher Fitbit/Health Connect, tryk åbner FoodAdvisor |
| F19 | Skrivemaskine-effekt | Dagens tip, velkomsten og tomme tilstande skrives frem tegn for tegn (16 ms pr. tegn, højst 1,5 s) med en blinkende accent-markør. Kører én gang pr. tekst pr. dag; et tryk springer over. Med „Fjern animationer“ slået til vises teksten straks. Skærmlæseren får hele teksten med det samme. Bruges aldrig på app-navne, søgeresultater, tal eller fejl | UI-test (fuld tekst efter 1,5 s, tryk viser alt, kun én gang pr. dag, ingen animation med animationer slået fra) + TalkBack-test |
| F20 | Tre sider på hjemskærmen | Side 1 „i dag“: ur, næste aftale, fokus, dagens tip. Side 2 „træning“: dagens pas med ugeoversigt, skridt, forbrændt. Side 3 „byg“: dagens tip i stort format og tidligere tips. Hver side har sin egen række med 4 genveje; dock, søgefelt og sideprikker står fast nederst på alle sider. Skift med vandret swipe eller tryk på prikkerne (44 dp trykflade). Hjem-knappen viser altid side 1 | UI-test (swipe skifter side, prikker følger med, Hjem nulstiller til side 1, favoritter flytter sig ikke) + manuel med én hånd |
| F21 | Din egen baggrund | Hjemskærmen viser den baggrund, der allerede er sat på telefonen (temaet windowShowWallpaper). DarkIO læser, kopierer og ændrer aldrig billedet og beder ikke om tilladelse til det. En toning (80 % top, ca. 35 % midt, 95 % bund) holder teksten læsbar; topniveauet kan justeres 60–90 % i Indstillinger. Skuffe, søgning og indstillinger er helt mørke | Manuel: din nuværende baggrund ses efter skift til DarkIO og er uændret efter skift tilbage. UI-test af kontrast med en hvid og en sort testbaggrund |

## Ikke-funktionelle krav

En launcher kører hele tiden og ser alle dine apps, så hastighed og privatliv vejer lige så tungt som funktionerne.

| ID | Område | Krav | Bevis |
| --- | --- | --- | --- |
| N1 | Hastighed | Dashboard vises under 500 ms efter Hjem; skuffen åbner under 150 ms og scroller uden hak | Macrobenchmark-test + manuel på telefon |
| N2 | Hukommelse | Ingen hukommelseslæk efter 50 åbn/luk af skuffen | LeakCanary i debug-build |
| N3 | Stabilitet | Fejler et kort (fx skridt), vises en fejltekst i kortet, og resten af hjemskærmen virker | Unit-test der kaster fejl i hvert kort |
| N4 | Tilladelser | Kun: kalender, udvid statuslinje, adgang til brugsdata (PACKAGE\_USAGE\_STATS) og Health Connect READ\_STEPS og READ\_TOTAL\_CALORIES\_BURNED, samt REQUEST\_DELETE\_PACKAGES (gives ved installation; Android viser altid sin egen bekræftelse ved afinstallation). Hver tilladelse spørges først, når kortet slås til. Ingen INTERNET. **Ingen** QUERY\_ALL\_PACKAGES | Security-agent læser manifest |
| N5 | Privatliv | Intet forlader telefonen: DarkIO har ingen internetadgang. Sundheds-, kalender- og brugsdata læses kun og gemmes ikke af DarkIO. Ingen analytics eller reklame-SDK’er | Security-agent gennemgår afhængigheder og netværkskald |
| N7 | Ærlighed | „Skjul apps“ er en oprydning, ikke en lås. Det står i appen | UI/UX-agent |
| N8 | Tilgængelighed | Trykflader mindst 48 dp, kontrast mindst 4,5:1, TalkBack-labels, følger systemets skriftstørrelse og „reducer animationer“ | Accessibility Scanner + UI-test med stor skrift |
| N9 | Hardening | Release-build: R8 slået til, ikke debuggable, ingen ukrypteret HTTP, kun hjem-aktiviteten er eksporteret, plus den aktivitet Health Connect kræver for at vise, hvad adgangen bruges til | Security-agent læser manifest og build-fil |
| N10 | Batteri | Intet baggrundsarbejde. Kortene opdateres kun, når hjemskærmen vises | Manuel med Battery Historian eller indstillinger → batteri |
| N11 | Pixel og Android 16 | Tegner edge-to-edge (påkrævet i Android 16), men alt trykbart holder afstand til systemets gesture-zone nederst. Swipe op til skuffen starter over den zone, så den ikke kolliderer med Androids egen Hjem-gesture | UI-test med gesture-navigation på Pixel 10-emulator + manuel på telefonen |

## Data og eksisterende tilstand

Appen gemmer kun dine valg lokalt; listen over apps hentes altid frisk fra Android. Hver app identificeres af pakkenavn + aktivitet + brugerprofil, aldrig af sit navn, så en omdøbt app beholder sine valg.

**Hvad gemmes:** indstillinger (accentfarve, kort og gestures til/fra) i DataStore, og per app: skjult, favoritplads og egen kategori i en lille Room-database. Træningsprogram, mål og „gennemført i dag“ gemmes også lokalt. Tips ligger i appen som en fast liste.

| Situation | Hvad sker der | Test |
| --- | --- | --- |
| Første start | Ingen favoritter, automatiske kategorier, ur, træning og tip til. Kort der kræver adgang (kalender, fokus, skridt, forbrændt) viser „giv adgang“, så der ikke spørges om noget ved første start. En kort hint-tekst viser swipe op | UI-test på ren installation |
| Opdatering af launcheren | Databasen migreres. Dine favoritter, skjulte apps og kategorivalg bevares altid; en migration må aldrig slette brugerdata | Room-migrationstest fra hver tidligere version |
| Ny app installeres | Dukker op i skuffen med automatisk kategori | Manuel (F14) |
| App afinstalleres | Forsvinder fra skuffe og favoritter. Dens valg gemmes, så en geninstallation husker dem; „Nulstil alt“ rydder dem | Unit + manuel |
| Arbejdsprofil | Understøttes ikke i v1: arbejdsapps vises ikke i DarkIO, men åbnes fra Androids indstillinger eller notifikationer. Personlige apps påvirkes ikke | Manuel på din Pixel med arbejdsprofil: ingen arbejdsapps i listen, intet crasher |
| Du skifter fra din nuværende launcher | Dens layout importeres ikke. Den gamle launcher røres ikke og kan vælges igen under Indstillinger → Apps → Standardapps | Manuel |
| Ny telefon | Indstillinger følger med Androids backup; valg for apps, der ikke findes på den nye telefon, ignoreres | Manuel (valgfri) |
| Din nuværende baggrund | Beholdes som den er. DarkIO viser den og rører den aldrig; skifter du baggrund i Android, følger DarkIO med. Skifter du tilbage til din gamle launcher, er baggrunden den samme | Manuel (F21) |

Begge stier, **ren installation** og **opdatering med eksisterende data**, skal testes i verifikationsrunden.

## Teknisk arkitektur

Kotlin og Jetpack Compose i ét app-modul, delt i fire lag, så hver bygge-agent arbejder i sit eget område.

&#91;embedded content: arkitektur · 4 lag\]

- **App-listen** hentes med LauncherApps, og manifestet erklærer en `<queries>`-blok for startbare apps i stedet for den brede QUERY\_ALL\_PACKAGES.
- **Afhængigheder** holdes på et minimum: Compose, Room, DataStore, Coroutines og Health Connect-klienten. Intet netværksbibliotek. Ingen analytics eller reklame-SDK’er (N5).
- **Tests:** JUnit og Turbine til logik, Compose UI-test til skærme, Macrobenchmark til hastighed (N1) i et separat `:benchmark`-testmodul, LeakCanary kun i debug (N2).

**Design:** alle farver, fonte, afstande og radier kommer fra [DarkIO Design System](https://claude.ai/artifact/VrFv8skBrNACPQeoAPGR2g), som også har de færdige Compose-objekter. Fontene lægges i appen og hentes ikke over nettet (N5). En værdi, der ikke står der, tilføjes først til designsystemet.

## Byg- og testplan

Arbejdet deles i fire bygge-agenter og tre test-agenter. Test-agenterne har ikke set koden blive skrevet, så de ikke godkender deres eget arbejde.

| Agent | Ansvar | Krav | Starter |
| --- | --- | --- | --- |
| 1. Fundament | Projekt, manifest, hjem-aktivitet, app-liste via LauncherApps, database og indstillinger, fælles tema | F1, F14, N4, N9 | Først, alene |
| 2. Skuffe | App-skuffe, kategorier, søgning, skjul apps, app-menu | F7–F10, F12, N1 | Efter 1, parallelt |
| 3. Dashboard | Ur-, kalender-, trænings-, fokus-, skridt-, kalorie- og tip-kort med fejlisolering | F2–F5, F16–F18, N3, N10 | Efter 1, parallelt |
| 4. Hjem og gestures | Favoritter, gestures, indstillingsskærm | F6, F11, F13 | Efter 1, parallelt |
| 5. QA | Kører alle tests, skriver manglende, tester ren installation og opdatering | Alle F og afsnit 5 | Efter 2–4 |
| 6. Security | Manifest, tilladelser, afhængigheder, netværkskald, eksporterede komponenter | N4, N5, N9 | Efter 2–4 |
| 7. UI/UX | Sammenligner med skærmdesignet, kontrast, trykflader, TalkBack, stor skrift | N7, N8 + design | Efter 2–4 |

**Regler for bygge-agenterne**

1. Hver agent får dette dokument og skærmdesignet og arbejder kun på sine egne krav-ID’er.
2. Testen for et krav skrives først og skal fejle, før koden skrives.
3. Agent 1 fastlægger de fælles grænseflader (app-liste, indstillinger, tema), før 2–4 starter. De må ikke ændres uden at alle agenter får besked.
4. Hver agent afleverer én pull request med en liste: krav-ID → test der beviser det.

**Verifikationsrunden** (før noget installeres som din faste hjemskærm)

- [ ] Alle automatiske tests grønne på Pixel 10-emulator med Android 16 og på din egen Pixel 10
- [ ] Ren installation og opdatering med eksisterende data begge testet
- [ ] Security-rapport uden kritiske eller høje fund
- [ ] UI/UX-rapport med skærmbilleder af hver skærm i lys og stor skrift
- [ ] Manuelle testskridt for F1, F3, F5, F11, F14 og F18 udført på din telefon
- [ ] Fund sendes tilbage til bygge-agenten; maks. 3 runder, derefter tager du stilling

## Uden for v1 og åbne spørgsmål

**Ikke med i v1:** Android-widgets fra andre apps, flere hjemskærmssider, mapper, notifikationsprikker, egne ikonpakker, arbejdsprofil, dobbelttryk for at låse skærmen, import af layout fra din nuværende launcher, udgivelse på Google Play. Kendt begrænsning på Pixel: animationen ved app-skift og "seneste apps" er mindre glat med en tredjeparts-launcher end med Pixels egen, og det kan en launcher ikke ændre.

**Åbne spørgsmål**

- [x] Afklaret: Pixel 10 med Android 16.
- [x] Afklaret: FoodAdvisor skriver ikke ernæring til Health Connect (kun vægt). F18 viser derfor forbrændte kalorier med genvej til FoodAdvisor (Foodvisor, pakke `io.foodvisor.foodvisor`).
- [x] Afklaret: dobbelttryk-lås er ikke med i v1.
- [x] Afklaret: arbejdsprofil findes, men understøttes ikke i v1.
- [x] Afklaret: navnet er DarkIO.
