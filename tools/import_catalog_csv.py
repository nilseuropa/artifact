#!/usr/bin/env python3
"""Build the baked-in Artifact catalog asset from Directus CSV exports."""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path


def clean(value: object) -> str:
    return str(value or "").strip()


def first(row: dict[str, str], *names: str) -> str:
    normalized = {key.strip().lower(): value for key, value in row.items()}
    for name in names:
        value = clean(normalized.get(name.lower()))
        if value:
            return value
    return ""


def parse_year(value: str) -> int | None:
    value = clean(value)
    if not value:
        return None
    try:
        return int(value)
    except ValueError:
        return None


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8-sig") as handle:
        return list(csv.DictReader(handle))


def build_catalog(
    manufacturers_csv: Path,
    models_csv: Path,
) -> dict[str, list[dict[str, object]]]:
    manufacturers: dict[str, dict[str, object]] = {}
    manufacturers_by_name: dict[str, str] = {}

    for row_number, row in enumerate(read_csv(manufacturers_csv), start=1):
        manufacturer_id = first(row, "id", "manufacturer_id")
        name = first(row, "name", "manufacturer.name")
        if not manufacturer_id:
            raise ValueError(f"Manufacturer row {row_number} is missing id")
        if not name:
            raise ValueError(f"Manufacturer row {row_number} is missing name")
        if manufacturer_id in manufacturers:
            raise ValueError(f"Duplicate manufacturer id {manufacturer_id}")
        if name.casefold() in manufacturers_by_name:
            raise ValueError(f"Duplicate manufacturer name {name}")
        manufacturer = {
            "id": manufacturer_id,
            "name": name,
            "models": [],
        }
        manufacturers[manufacturer_id] = manufacturer
        manufacturers_by_name[name.casefold()] = manufacturer_id

    for row_number, row in enumerate(read_csv(models_csv), start=1):
        model_id = first(row, "id", "model_id")
        name = first(row, "name", "model", "model.name")
        manufacturer_id = first(row, "manufacturer", "manufacturer.id", "manufacturer_id")
        manufacturer_name = first(row, "manufacturer.name", "manufacturer_name")

        if not manufacturer_id and manufacturer_name:
            manufacturer_id = manufacturers_by_name.get(manufacturer_name.casefold(), "")

        if not name or not manufacturer_id:
            continue

        if not model_id:
            model_id = f"{manufacturer_id}:{name.casefold()}" or str(row_number)

        if manufacturer_id not in manufacturers:
            raise ValueError(
                f"Model '{name}' references unknown manufacturer id {manufacturer_id}. "
                "The manufacturer column must match an id in manufacturers.csv.",
            )

        production_year_start = parse_year(
            first(row, "production_year_start", "production year start"),
        )
        production_year_end = parse_year(
            first(row, "production_year_end", "production year end"),
        )
        if (
            production_year_start is not None
            and production_year_end is not None
            and production_year_start > production_year_end
        ):
            raise ValueError(
                f"Model '{name}' has production_year_start after production_year_end",
            )

        model = {
            "id": model_id,
            "name": name,
            "productionYearStart": production_year_start,
            "productionYearEnd": production_year_end,
        }
        manufacturers[manufacturer_id]["models"].append(model)

    for manufacturer in manufacturers.values():
        seen_models: set[str] = set()
        for model in manufacturer["models"]:
            model_name = clean(model["name"]).casefold()
            if model_name in seen_models:
                raise ValueError(
                    f"Duplicate model '{model['name']}' for manufacturer {manufacturer['name']}",
                )
            seen_models.add(model_name)

    sorted_manufacturers = sorted(
        manufacturers.values(),
        key=lambda item: clean(item["name"]).casefold(),
    )
    for manufacturer in sorted_manufacturers:
        manufacturer["models"] = sorted(
            manufacturer["models"],
            key=lambda item: clean(item["name"]).casefold(),
        )

    return {"manufacturers": sorted_manufacturers}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--manufacturers",
        default="import/manufacturers.csv",
        type=Path,
        help="Directus manufacturers CSV export",
    )
    parser.add_argument(
        "--models",
        default="import/manufacturer_models.csv",
        type=Path,
        help="Directus manufacturer_models CSV export",
    )
    parser.add_argument(
        "--output",
        default="app/src/main/assets/catalog_seed.json",
        type=Path,
        help="Android asset output path",
    )
    args = parser.parse_args()

    catalog = build_catalog(
        args.manufacturers,
        args.models,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(catalog, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    manufacturer_count = len(catalog["manufacturers"])
    model_count = sum(len(item["models"]) for item in catalog["manufacturers"])
    print(f"Wrote {manufacturer_count} manufacturers and {model_count} models to {args.output}")


if __name__ == "__main__":
    main()
