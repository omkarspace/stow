# Security Policy

## Supported Versions

Only the latest release on the `main` branch receives security and engine updates.

| Version | Supported |
| ------- | --------- |
| 3.0.x   | ✅ Yes     |
| < 3.0   | ❌ No      |

## Privacy & Security Guarantees

- **100% Client-Side:** Stow never transmits pasted URLs, device identifiers, or download history to any developer-controlled server.
- **Scoped Storage:** Stow writes exclusively through Android's `MediaStore` API (`Movies/Stow`, `Music/Stow`, `Pictures/Stow`).
- **Verifiable Builds:** Every official GitHub Release ships with `checksums.txt` (SHA-256) for all split APKs.

## Reporting a Vulnerability

If you discover a security issue (such as intent hijacking, path traversal in file naming, or unsafe WebView behavior):

1. **Do not** open a public GitHub issue with exploit details.
2. Use GitHub's **Security Advisories** (*Security → Report a vulnerability*) on [omkarspace/stow](https://github.com/omkarspace/stow/security).
3. Include steps to reproduce, affected version, and suggested mitigation if known.
