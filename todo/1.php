<?php

class Foo
{
    /** @var array{label: string}[] */
    public array $buttons = [];

    public function __construct()
    {
        foreach ($this->buttons as $b) {
            $b['label'] = 10;
            $b['label2'] = 10; // TEST: must show error
        }
    }
}
