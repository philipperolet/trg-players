# Philosophie
- code "sale" (pas vraiment) 4* plus vite, sans forcément tester ou tout bien calibrer parfaitement. 
 - Refacto après (en notes), c'est plus efficace et plus fun
   - refacto noté plutôt que fait => permet d'éviter l'overeng
 - attention à éviter l'overeng!!
- how will it be the funniest to get this feature done?
- c'est un nouveau style. Prends le temps et l'effort pour tester. Ça peut être très kiffant
- écriture SLA high-level first

# Clean guidelines
- **Nesting < 2**
  - nesting : let = 1/2, thread = 1/2, fn inside let = 1/2, args on multi line = 1/2
- **Doc**
  - pas plus de 10l par fonction. Le reste => doc à coté dans arch
  - le but : lisible. Mais lisible pour toi, pas pour les autres
- **KISS**
  - Quand tu ajoutes des trucs, il faut si possible simplifier (enlever des params, abstractions plus simples)

# Release
- Do last code commit
- remove trails from version
- write changelog from commits in project.md
- commit, push, merge on master
- tag
  - vX.X.X-m0.X.X-release-name - changelog in tag message
  - push tag
- create new branch, bump version to next in this branch
