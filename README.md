# ZamekZPiasku

Plugin dla Paper 1.21.4 (Java 21).

## Uzycie
- `/msitems` - otwiera menu z przedmiotami (OP / `zamek.admin`). Klikasz przedmiot, zeby go odebrac.
  - **Zamek z Piasku** - PPM tym przedmiotem wkleja budowle ze schematu na 7 sekund.
- Po 7 sekundach plugin przywraca zapisane bloki.
- Bloki eventu sa chronione przed niszczeniem, stawianiem, wybuchami, ogniem i tlokami.

- `/msitems stacks <zamek|kokos|lod> <on|off>` - wlacza/wylacza stackowanie przedmiotu (zapis w `config.yml`, dotyczy nowo odebranych sztuk).
- Kokos swieci jak enchantowany.
- **Dzwonek Szkolny** (zloty samorodek) - PPM w powietrzu: fale halasu (kola Wardena) tam, gdzie patrzysz. Trafieni gracze
  (ktorzy maja pod soba blok) sa sciagani w dol i nie moga uzywac elytry ani fajerwerkow do wyladowania.
  Dziala tylko powyzej wysokosci `bell.min-y` z `config.yml` (domyslnie Y=100).
- **Smoczy Odlamek** - zwykly przedmiot do wymiany u NPC.

## Tekstury
Przedmioty uzywaja `item_model` `ms:zamek`, `ms:kokos`, `ms:lod_na_patyku`. Dolacz `MSItems-ResourcePack.zip`
(albo skopiuj folder `assets/ms/` do swojego resource packa). Bez packa gracze widza zwykle przedmioty (piasek, ciasteczko, patyk).

## Schemat
Budowla jest wczytywana ze schematu `src/main/resources/zamekzpiasku.schem` (WorldEdit / Sponge v2-v3, 11x9x11).
Aby ja zmienic, podmien ten plik (zachowujac nazwe) i zbuduj plugin ponownie.
Budowla jest wysrodkowana na graczu, a jej podloga (warstwa 0) laduje tuz pod jego stopami.
Wklejane sa bloki budowli i puste wnetrze; powietrze na zewnatrz budowli nie wycina terenu.

## Dodawanie kolejnych przedmiotow
W `ZamekZPiasku.onEnable()` dodaj linijke `menu.register(slot, () -> twojItem);` (slot 0-26).

## Budowanie
Wgraj projekt na GitHub i uruchom workflow `Build ZamekZPiasku` w zakladce Actions. JAR znajdziesz w artefakcie `ZamekZPiasku-1.21.4`.
