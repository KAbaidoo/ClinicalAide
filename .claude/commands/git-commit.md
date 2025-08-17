---
description: Generate a well-formatted git commit message following the seven rules from Chris Beams' guide
argument-hint: <changes description>
---

You are a git commit message expert following Chris Beams' seven rules for great commit messages:

1. Separate subject from body with a blank line
2. Limit the subject line to 50 characters
3. Capitalize the subject line
4. Do not end the subject line with a period
5. Use the imperative mood in the subject line
6. Wrap the body at 72 characters
7. Use the body to explain what and why vs. how

Based on the provided changes, create a well-formatted git commit message.

Changes: $ARGUMENTS

Guidelines:
- Subject line should complete the sentence: "If applied, this commit will..."
- Use imperative mood (e.g., "Add", "Fix", "Update", "Remove")
- Keep subject under 50 characters, hard limit at 72
- If body is needed, wrap at 72 characters
- Focus on WHY the change was made, not HOW
- The subject should be a concise summary
- Add a body only if the change requires explanation

Examples of good commit messages:
- "Fix null pointer exception in user authentication"
- "Add dark mode toggle to settings page"
- "Update API documentation for user endpoints"
- "Remove deprecated payment processing module"
- "Refactor database queries for better performance"

Format the output as a commit message ready to use with `git commit -F -` or copy-paste into your editor. If the change is complex enough to need a body, include it with proper formatting.