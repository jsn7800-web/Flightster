# Flightster til Android

Fuldskærmsappen og en widget til hjemmeskærmen.

## Hvad der er hvad

**Appen** er den samme enkeltfils-webapp som kører på væggen, vist i en WebView
i fuldskærm med skærmen holdt tændt. Den kører rigtigt live med to sekunders
opdatering.

**Widgeten** er tegnet nativt. Det er ikke et valg — Android-widgets bygger på
RemoteViews, som ikke understøtter WebView, så der kan ikke køre JavaScript i
en widget. Hele panelet tegnes derfor på et Canvas og lægges i én ImageView.
Tegnemotoren i `AircraftDraw.kt` er en direkte oversættelse af den fra
webudgaven: formen udledes af flyets faktiske mål, så en 747 får fire motorer
og overdæk, og en Cessna får propel og lige vinger.

## Om de 30 minutter

Android tillader ikke hyppigere baggrundsopdatering. WorkManager kan i praksis
komme ned på 15 minutter, og widget-mekanismens egen grænse er 30. Et fly er
inden for synsvidde i omkring to minutter, så en widget kan aldrig være live.

Widgeten er derfor bygget som en logbog: den viser det seneste fly der *var*
over dig, med et ærligt tidsstempel — "for 12 minutter siden". Tryk på den for
at åbne appen, som kører live.

## Byg

### Uden computer: lad GitHub gøre det

Android Studio findes ikke til Android, så hvis du kun har telefonen, er den
letteste vej at bygge i skyen.

1. Opret et nyt, privat repo på github.com.
2. Upload indholdet af denne mappe. Fra telefonen kan du bruge
   `Add file > Upload files` i browseren, eller GitHub-appen.
3. Gå til fanen **Actions**, vælg **Byg Flightster APK**, tryk
   **Run workflow**.
4. Efter et par minutter ligger APK'en under **Artifacts** på kørslen.
   Hent den, pak zip-filen ud, og åbn APK'en.

Workflowet ligger i `.github/workflows/build.yml`. Det er gratis for
offentlige repos, og private repos har en rundhåndet månedlig kvote som
et projekt af denne størrelse aldrig når i nærheden af.

Bemærk at workflowet kalder `gradle` direkte i stedet for `./gradlew`,
fordi wrapperens jar-fil ikke er med i mappen. Åbner du projektet i
Android Studio, laver den selv wrapperen.

### Med computer

1. Åbn mappen i Android Studio (Ladybug eller nyere).
2. Lad den hente Gradle og SDK ved første åbning.
3. `Build > Build Bundle(s) / APK(s) > Build APK(s)`.

APK'en lander i `app/build/outputs/apk/debug/app-debug.apk`.

## Sideload

Overfør APK'en til telefonen og åbn den. Første gang skal du give din
filhåndtering eller browser lov til at installere ukendte apps under
`Indstillinger > Apps > Særlig adgang`.

En debug-APK er signeret med en debug-nøgle og kan installeres direkte.
Skal den holde længere end et år, eller skal du kunne opdatere den uden at
afinstallere først, så lav en rigtig nøgle med
`Build > Generate Signed Bundle / APK`.

## Opsætning

Læg widgeten på hjemmeskærmen, så åbner opsætningsskærmen. Her sætter du:

- **Adresse** — din egen Flightster, som standard `https://flightster.netlify.app`.
  Widgeten kalder `/api/now` på den adresse, så Netlify-funktionen skal være
  deployet.
- **Radius og højdevinkel** — samme betydning som i webappen.
- **Følg min position** — henter sidst kendte position fra systemet ved hver
  opdatering. Der bruges bevidst ikke baggrundslokation: det koster batteri
  uden at give noget, når intervallet alligevel er en halv time.
- **Foto** — slår Planespotters-opslaget til og fra.

## Kendte begrænsninger

Fotoet skaleres ned til 640 px bredde. RemoteViews sender widgetens indhold
over IPC med en grænse omkring 1 MB, og et for stort bitmap får widgeten til
at fejle tavst uden fejlmeddelelse. Af samme grund har `pixelSize()` et loft
på antallet af pixels i panelet.

Batterioptimering kan udskyde opdateringer yderligere. Vil du have dem til
tiden, så undtag Flightster under `Indstillinger > Batteri`.
