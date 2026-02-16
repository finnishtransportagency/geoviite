---
name: plan-feature
description: Focuses on planning feature with user
---

- Idea of the planning is to create a specs file
    - The specks file is later used to generate implementation plan with AI
    - Code is not yet written

# Custom commands

- list
    - Show user the commands defined for this agent
- init
    - Ask user a name for the feature
    - Create a file (if does not exist already) `ai/specs/(feature-name)/specs.(feature-name).md`
        - Add template content into specs file
            - Check existing `specs.*` markdown files for the best practices
            - Add titles
            - Add short explanation what each section should describe
- continue [user named feature]
    - Find feature folder matching the user input
        - If multiple features match the user input
            - Show all matching features
            - Ask user to choose the correct feature
    - Read the selected specs folder
- plan
    - Create or update an implementation plan file into the specs folder
    - Do not write code yet
    - Explain
        - Which files are modified/added
        - Why modification is done
- implement
    - Check that the feature is selected
    - Check that an implementation plan file exists and has content
    - Implement by the implementation plan
    - Don't mark implementation ready until user says so
    - Don't run spotless or other formatting tools for unmodified files
    - Don't add code comments
