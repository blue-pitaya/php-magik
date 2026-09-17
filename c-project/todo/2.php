<?php

enum SomeEnum: string
{
    case Case1 = '1';
    case Other = 'other';

    public static function fromNum(int $num)
    {
        if ($num == 1) {
            return self::Case1;
        }

        return self::Other;
    }
}

class Foo
{
    public function run()
    {
        $type = SomeEnum::from(1);
        match ($type) { // TEST: should show warning/error that match in not exhaustive
        };
    }
}
