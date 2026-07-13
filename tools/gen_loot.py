#!/usr/bin/env python3
"""Génère les loot tables « drop self » des blocs custom MineAzur.

POURQUOI : en MC moderne (contrairement à 1.2.5), un bloc ne droppe QUE ce que sa loot table décrit. La table
est résolue par convention depuis l'id du bloc : `mineazur:<name>` -> `mineazur:blocks/<name>`
(cf. BlockBehaviour.Properties.drops). Sans fichier, la table est vide -> le bloc ne droppe RIEN en survie
(invisible en créatif, d'où le trou passé inaperçu). Ce script écrit une table standard « casse -> rend l'item »
pour chaque bloc, en lisant l'ordre depuis la source unique shared/mineazur_blocks.json.

Rejouable : à relancer si on ajoute un bloc. Miroir de gen_recipes.py.
"""
import json, os

BLOCKS_JSON = "/home/sebartyr/mineazur/shared/mineazur_blocks.json"
OUT = "/home/sebartyr/mineazur/Paper/paper-server/src/main/resources/data/mineazur/loot_table/blocks"
os.makedirs(OUT, exist_ok=True)

def drop_self(name):
    """Table de bloc vanilla-standard : 1 roll, drop l'item du même nom, survit à l'explosion."""
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1.0,
            "bonus_rolls": 0.0,
            "entries": [{"type": "minecraft:item", "name": f"mineazur:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    }

blocks = json.load(open(BLOCKS_JSON))["blocks"]
for b in blocks:
    name = b["name"]
    json.dump(drop_self(name), open(f"{OUT}/{name}.json", "w"), indent=2)

print(f"{len(blocks)} loot tables 'drop self' générées dans data/mineazur/loot_table/blocks/")
