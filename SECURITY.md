# Security Policy

## Reporting a vulnerability

Please **do not open a public issue** for security problems.

Use GitHub's private reporting instead: open the **Security** tab of this repository and choose
**Report a vulnerability**. If that is not available, contact the maintainers privately through the
contact details on their GitHub profiles. We aim to acknowledge reports within a few days.

## What is in scope

ratchet4j reads files produced by other tools and files from your repository, so the most
relevant problems are:

- parsing flaws in the report readers (XML external entity injection, resource exhaustion, crashes
  on hostile input);
- path handling flaws (reading or writing outside the repository root);
- anything that makes `check` report "no new violations" when there are some.

## Supported versions

The project is pre-1.0. Only the latest release (and `main`) receives security fixes.
