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

/**
 * @phoenix-view resources/views
 */
function view(string $name, array $data = []): void
{
    static $engine = null;

    $engine ??= new Engine(projectDir('resources/views'));

    echo $engine->render($name, $data);
}

view('admin/home'); // resources/views/admin/home.php
```

O autocomplete mostra nomes de templates sem extensão e a navegação abre o arquivo correspondente.

Use `@phoenix-view` no PHPDoc da função ou do método que recebe o nome do template como primeiro parâmetro. O nome da função ou do método é livre; funciona com métodos de instância, métodos estáticos e funções importadas com alias.

A pasta é relativa à raiz do projeto, não ao arquivo do helper. Em projetos com múltiplas raízes de conteúdo, usa a raiz do arquivo que faz a chamada. Aceita `resources/views` e `resources\views`; não use caminhos absolutos ou `..`.

O autocomplete também funciona em subpastas e com argumentos nomeados. <kbd>Ctrl</kbd> + clique ou <kbd>Ctrl</kbd> + <kbd>B</kbd> no nome abre o template.

A anotação tem prioridade sobre a detecção do corpo da função. Sem anotação, a detecção de `new Engine(__DIR__ . '...')` em `view()` continua disponível. Uma pasta anotada inexistente não gera sugestões de outro diretório. A anotação é usada apenas pela IDE e não altera a execução do PHP.

## env

```php
function env(string $key, mixed $default = null): mixed
{
    return $_ENV[$key] ?? $default;
}

env('ADMIN_ROUTER');
```

As chaves são lidas do `.env`. A sugestão é case-insensitive, mas a inserção sempre mantém exatamente o nome definido no arquivo — por exemplo, `ADMIN_ROUTER`.

## Struct do model

Associe um model de `Rider\System\Database\Model` à classe gerada com `@phoenix-struct`:

```php
namespace Seek\Horus\Model;

use Rider\System\Database\Model;

/**
 * @phoenix-struct \Seek\Horus\Struct\MailData
 */
class Mail extends Model
{
    protected string $table = 'mail';
}
```

O PhpStorm passa a usar as propriedades e os tipos de `MailData` nos resultados das consultas:

```php
$model = new Mail();
$mail = $model->random();
$mail?->host;

foreach ($model->findAll() as $mail) {
    echo $mail->email;
}

$model->orderBy('id')->findById(1)?->port;
```

A anotação fornece contexto para `findById()`, `first()`, `random()`, `findAll()`, `whereIn()`, `whereNotIn()` e `search()`. Em `findBy()` e `between()`, o argumento `all` distingue registro único e coleção; quando seu valor não é conhecido estaticamente, ambas as possibilidades são mantidas.

`orderBy()` continua retornando o model. Métodos sobrescritos, modelos sem anotação, agregações e SQL livre (`sum()` e `query()`) mantêm a inferência nativa.

A inferência é complementar: o PhpStorm pode continuar exibindo `object`, `array` ou `mixed` junto ao contexto específico da struct. A anotação não substitui os tipos nativos do método.

Isso existe apenas na IDE: não instancia `MailData`, não hidrata registros e não altera retornos ou execução do ORM. O objeto real continua sendo o retornado pelo banco; a anotação não garante `instanceof MailData`.

---

Desenvolvido para **PhpStorm 2026.2+** · Requer Java 25 para desenvolver e executar o plugin.
