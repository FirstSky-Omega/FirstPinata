# FirstPinata

Plugin Minecraft Folia/Paper 1.26 — Piñatas interactifs avec classement par dégâts, récompenses configurables, planification automatique et intégrations MythicMobs, Nexo, ModelEngine et PlaceholderAPI.

## ⚙️ Stack

- **Folia API** 1.26 (Java 21) · **Folia-compatible** (Bukkit/Paper aussi)
- **MythicMobs** (soft-depend) — spawn de mobs custom + skills additionnels
- **Nexo** (soft-depend) — récompenses d'items custom (`give-nexo`)
- **ModelEngine** (soft-depend) — modèles 3D attachés aux piñatas
- **PlaceholderAPI** (soft-depend) — expansion `%pinata_*%`
- Stockage : **YAML** (par défaut) — pas de base de données requise

## 🛠 Build

```bash
mvn clean package
```

Le JAR est généré dans `target/`.

## 📋 Premier démarrage

1. Lance le plugin une première fois → les configs sont générées dans `plugins/FirstPinata/`.
2. Édite `config.yml` pour ajuster les paramètres globaux (concurrence, timezone, intégrations).
3. Crée tes types de piñata dans `pinatas/*.yml` (voir `pinatas/example.yml` pour le template exhaustif).
4. Définis tes zones dans `zones.yml`.
5. (Optionnel) Configure les spawns automatiques dans `schedules.yml`.
6. Redémarre le serveur.

## 🎪 Fonctionnement

Un piñata est un mob qui encaisse des dégâts de plusieurs joueurs simultanément. Le plugin suit les dégâts infligés par chaque joueur et distribue des récompenses à sa mort selon le classement (`tiers`) ou à chaque hit (`hit`). Un piñata peut aussi se despawn automatiquement si sa durée de vie ou son timer d'inactivité est dépassé.

**Types de mob :**
- `vanilla` — entité Bukkit standard (PIG, ZOMBIE, etc.)
- `mythic` — mob MythicMobs avec ses skills (keep-skills configurable)

**Intégration ModelEngine :** un model 3D est attaché au mob de base, qui est automatiquement masqué (`hide-base-mob: true`).

**Anti-cheese damage :**
- Cap par hit (`per-hit-cap`) et par joueur (`per-player-cap`)
- Cooldown entre deux hits (`cooldown-ms`)
- Whitelist / blacklist de matériaux (`allowed-materials`, `denied-materials`)
- Causes de dégâts bloquées (`blocked-causes`)

## 🎮 Commandes

| Commande | Description |
|---|---|
| `/pinata help` | Afficher l'aide |
| `/pinata spawn <type> [zone:<nom>]` | Faire apparaître un piñata |
| `/pinata stop <id\|all>` | Despawn d'un ou tous les piñatas actifs |
| `/pinata list` | Lister les piñatas actifs |
| `/pinata info <id>` | Infos détaillées sur un piñata actif |
| `/pinata zone <nom> ...` | Gérer les zones (add, remove, list) |
| `/pinata reload` | Recharger toute la configuration |

Alias : `/pin`, `/pt`

## 🔐 Permissions

| Permission | Default | Description |
|---|---|---|
| `pinata.admin` | op | Accès à toutes les commandes admin |
| `pinata.command.help` | tous | Aide |
| `pinata.command.spawn` | op | Spawner un piñata |
| `pinata.command.stop` | op | Stopper un piñata |
| `pinata.command.list` | op | Lister les piñatas actifs |
| `pinata.command.info` | op | Infos sur un piñata |
| `pinata.command.zone` | op | Gérer les zones |
| `pinata.command.reload` | op | Recharger la config |

## 📊 Placeholders (PAPI)

```
%pinata_active%                → nombre de piñatas actifs
%pinata_types_loaded%          → nombre de types configurés
%pinata_top_name_<type>%       → nom du top damager pour un type actif
%pinata_top_damage_<type>%     → dégâts du top damager (entier)
%pinata_player_hits_<type>%    → dégâts totaux du joueur sur ce type actif
%pinata_player_top_rank%       → meilleur rang actuel du joueur sur un piñata actif
```

Exemple : `%pinata_top_name_boss%` → nom du joueur en tête sur le piñata de type `boss`.

## 📁 Structure des configs

```
plugins/FirstPinata/
├── config.yml          — config globale (langue, concurrence, intégrations)
├── zones.yml           — zones de spawn (box, single, points)
├── schedules.yml       — spawns automatiques (interval, fixed-times)
├── messages_fr.yml     — messages (MiniMessage)
└── pinatas/
    ├── example.yml     — template annoté complet
    └── boss.yml        — exemple de piñata boss
```

## 🎁 Format des actions (récompenses)

```yaml
# Dans un fichier pinatas/*.yml, section rewards
actions:
  - "console: eco give <player> 500"        # commande console (placeholders OK)
  - "player: <cmd>"                          # commande joueur
  - "message: <green>Bravo !"               # message MiniMessage
  - "broadcast: <gold><player> a gagné !"   # broadcast global
  - "title: <title>;<sub>;<in>;<stay>;<out>"
  - "actionbar: <text>"
  - "sound: ENTITY_EXPERIENCE_ORB_PICKUP,1.0,1.5"
  - "give: minecraft:diamond 5"             # item vanilla
  - "give-nexo: nexo_item_id 1"            # item Nexo
  - "effect: SPEED 10 1"                    # effet de potion
  - "wait: 20"                              # délai en ticks
```

Placeholders disponibles dans les actions : `<player>`, `<top>`, `<pinata>`, `<pinata_id>`, `<participants>`, `<world>`, `<x>`, `<y>`, `<z>` + tous les placeholders PAPI.
