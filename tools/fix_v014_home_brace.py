from pathlib import Path

path = Path("app/src/main/java/com/yourname/pdftoolkit/ui/screens/PremiumToolsScreen.kt")
text = path.read_text(encoding="utf-8")
old = """                items(visibleTools, key = { it.source.id }) { tool ->
                    PremiumToolCard(
                        tool = tool,
                        expanded = expanded,
                        onClick = { openTool(tool.source) },
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun WorkspaceHero"""
new = """                items(visibleTools, key = { it.source.id }) { tool ->
                    PremiumToolCard(
                        tool = tool,
                        expanded = expanded,
                        onClick = { openTool(tool.source) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceHero"""
if old not in text:
    raise SystemExit("Expected duplicate home brace pattern was not found")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
