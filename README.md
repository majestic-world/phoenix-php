# Phoenix PHP

> Melhor suporte para helpers PHP no **PhpStorm 2026.2+**.

O Phoenix PHP adiciona autocomplete e navegação para helpers simples que normalmente não podem ser entendidos pelo IDE.

| Helper | O que o plugin entrega |
| --- | --- |
| `projectDir()` | Arquivos e pastas relativos a `dirname(__DIR__, N)` |
| `view()` | Templates do Plates sem a extensão do arquivo |
| `env()` | Chaves do arquivo `.env`, preservando o casing original |

## projectDir

```php
function projectDir(?string $path = null): string
{
    $root = dirname(__DIR__, 2);
    return $root . $path;
}

projectDir('/views/home.php');
```

Use <kbd>Ctrl</kbd> + <kbd>Space</kbd> para listar arquivos e pastas a partir da raiz calculada. <kbd>Ctrl</kbd> + clique ou <kbd>Ctrl</kbd> + <kbd>B</kbd> abre arquivos no editor e revela pastas na árvore do projeto.

## view

```php
use League\Plates\Engine;

function view(string $name, array $data = []): void
{
    $engine = new Engine(__DIR__ . '/../views');
    echo $engine->render($name, $data);
}

view('admin/home'); // views/admin/home.php
```

O autocomplete mostra nomes de templates sem extensão e a navegação abre o arquivo correspondente.

## env

```php
function env(string $key, mixed $default = null): mixed
{
    return $_ENV[$key] ?? $default;
}

env('ADMIN_ROUTER');
```

As chaves são lidas do `.env`. A sugestão é case-insensitive, mas a inserção sempre mantém exatamente o nome definido no arquivo — por exemplo, `ADMIN_ROUTER`.

---

Desenvolvido para **PhpStorm 2026.2+** · Requer Java 25 para desenvolver e executar o plugin.
